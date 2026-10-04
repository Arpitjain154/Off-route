"""Replay real LiveNavigation Java sources; withheld GPS is scoring-only."""
import csv
import hashlib
import json
import os
from pathlib import Path
import subprocess
import numpy as np
from read_drlog import load
from .common import ROOT
from .evaluate import compare_navigation


def source_hash():
    digest = hashlib.sha256()
    files = list((ROOT / 'android/app/src/main/java/com/idr/navigator/engine').glob('*.java'))
    files += list((ROOT / 'ml/laptop_agent').glob('*.py'))
    files += [ROOT / 'ml/phone_learning.py', ROOT / 'ml/read_drlog.py',
              ROOT / 'ml/java/LaptopNavigationReplay.java']
    for path in sorted(files):
        digest.update(str(path.relative_to(ROOT)).replace('\\', '/').encode())
        digest.update(path.read_bytes())
    return digest.hexdigest()


def java_tools():
    homes = [Path(os.environ.get('JAVA_HOME', '')),
             Path.home() / '.jdks/jbr-21.0.11', Path('C:/Program Files/Android/Android Studio/jbr')]
    for home in homes:
        if (home / 'bin/javac.exe').is_file():
            return home / 'bin/javac.exe', home / 'bin/java.exe'
    raise RuntimeError('Java JDK was not found. Install Android Studio or set JAVA_HOME, then retry.')


def compile_replay(output):
    javac, java = java_tools()
    jars = sorted((Path.home() / '.gradle/caches/modules-2/files-2.1/org.ejml').glob('**/*.jar'))
    # Gradle stores each module next to the group directory, below org.ejml.
    if not jars:
        raise RuntimeError('EJML dependencies missing. Run the launcher Prepare Android tests option first.')
    classes = output / 'java-classes'; classes.mkdir(exist_ok=True)
    classpath = os.pathsep.join(map(str, jars))
    source = ROOT / 'android/app/src/main/java'
    command = [str(javac), '-cp', classpath, '-sourcepath', str(source), '-d', str(classes),
               str(ROOT / 'ml/java/LaptopNavigationReplay.java')]
    with (output / 'javac.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=180)
    if result.returncode:
        raise RuntimeError('Navigation replay compilation failed; see ' + str(output / 'javac.log'))
    return [str(java), '-Xmx1g', '-cp', str(classes) + os.pathsep + classpath, 'LaptopNavigationReplay']


def write_replay(path, data, baseline, candidate, output, baseline_enabled):
    sid = path.stem
    mask = data['session'] == sid
    prediction = {int(t): (float(b), float(c)) for t, b, c in
                  zip(data['t_ns'][mask], baseline[mask], candidate[mask])}
    with path.with_suffix('.quality.csv').open() as f:
        quality = {int(r['t_ns']): r for r in csv.DictReader(f)}
    _, records = load(path)
    precise={}
    provenance=json.loads(path.with_suffix('.training.json').read_text())
    if provenance.get('rawSidecarsSchemaVersion') == 1:
        # Dataset timestamps identify receiver callbacks. Replay at the first available
        # IMU row after arrival, never before the observation was actually available.
        remapped={}
        with path.with_suffix('.gnss.csv').open() as f:
            for row in csv.DictReader(f):
                arrival=int(row['arrival_t_ns']);fix=int(row['fix_t_ns'])
                if arrival not in prediction or row['mock']!='0' or row['accepted']!='1':continue
                if not 0<=arrival-fix<=1_000_000_000:continue
                i=int(np.searchsorted(records['t_ns'],arrival,side='left'))
                if i>=len(records):continue
                replay_time=int(records['t_ns'][i])
                if replay_time-fix>1_000_000_000:continue
                precise[replay_time]=row;remapped[replay_time]=prediction[arrival]
        prediction=remapped
    start = int(records['t_ns'][0]); gravity = np.array([0., 0., 9.80665])
    last = start; previous = None; seen = set(); course = float('nan'); course_error = float('nan')
    with output.open('w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['t','ax','ay','az','gx','gy','gz','gravx','gravy','gravz','compass',
                         'gyro','fix_t','lat','lon','alt','accuracy','speed','speed_error','course',
                         'course_error','reference','hidden','base','candidate','base_enabled'])
        for r in records:
            t = int(r['t_ns']); q = quality.get(t)
            if q is None:
                continue
            observation=precise.get(t)
            if observation is not None:
                r={name:r[name] for name in records.dtype.names}
                for key,field in [('lat','latitude_deg'),('lon','longitude_deg'),('alt','altitude_m'),('horiz_acc','horizontal_accuracy_m'),('speed_mps','speed_mps')]:
                    r[key]=float(observation[field])
                q=dict(q,fix_t_ns=observation['fix_t_ns'],speed_accuracy_mps=observation['speed_accuracy_mps'])
            imu = np.array([r[k] for k in ('ax','ay','az','gx','gy','gz')], dtype=float)
            if not np.isfinite(imu).all():
                continue
            dt = max(0, min(.1, (t-last)*1e-9)); last = t
            norm = np.linalg.norm(imu[:3])
            if abs(norm-9.80665) < .8:
                gravity += (imu[:3]-gravity)*(1-np.exp(-dt/1.5))
            mag = np.array([r[k] for k in ('mx','my','mz')], dtype=float)
            east = np.cross(mag, gravity)
            compass = float('nan')
            if np.isfinite(mag).all() and 10 <= np.linalg.norm(mag) <= 100 and np.linalg.norm(east) > .01:
                east /= np.linalg.norm(east); up = gravity / np.linalg.norm(gravity)
                north = np.cross(up, east)
                compass = float(np.degrees(np.arctan2(east[1], north[1])) % 360)
            fix = int(q['fix_t_ns']); error = float(q['speed_accuracy_mps'])
            good = (t in prediction and 0 <= t-fix <= 1_000_000_000 and
                    np.isfinite([r['lat'],r['lon'],r['horiz_acc'],r['speed_mps'],error]).all() and
                    abs(r['lat']) <= 85 and abs(r['lon']) <= 180)
            hidden = (t-start)*1e-9 >= 60 and 60 <= ((t-start)*1e-9 % 120) < 90
            fresh = fix not in seen and good
            if fresh:
                seen.add(fix)
            # Hidden receiver observations cannot calibrate compass/course/velocity.
            if fresh and not hidden:
                course = float('nan'); course_error = float('nan')
                if previous is not None:
                    seconds = (fix-previous[0])*1e-9
                    north_d = (float(r['lat'])-previous[1])*111320
                    east_d = (float(r['lon'])-previous[2])*111320*np.cos(np.deg2rad(r['lat']))
                    distance = np.hypot(north_d, east_d)
                    uncertainty = np.hypot(float(r['horiz_acc']), previous[3])
                    if 0 < seconds <= 10 and distance > max(5, 3*uncertainty):
                        course = float(np.degrees(np.arctan2(east_d, north_d)) % 360)
                        course_error = float(np.degrees(np.arctan2(uncertainty, distance)))
                        previous = None
                    elif seconds > 10:
                        previous = None
                if previous is None:
                    previous = (fix, float(r['lat']), float(r['lon']), float(r['horiz_acc']))
            b, c = prediction.get(t, (float('nan'), float('nan')))
            writer.writerow([t, *imu, *gravity, compass, int(q['gyro_fresh']), fix,
                             float(r['lat']), float(r['lon']), float(r['alt']), float(r['horiz_acc']),
                             float(r['speed_mps']), error, course, course_error, int(fresh), int(hidden),
                             b, c, int(baseline_enabled)])


def replay(paths, data, baseline, candidate, output, baseline_enabled=False):
    replay_dir = output / 'replay'; replay_dir.mkdir(exist_ok=True)
    command = compile_replay(output)
    all_rows = []
    for path in paths:
        if path.stem not in set(data['session'][data['split'] == 'test']):
            continue
        source = replay_dir / (path.stem + '.input.csv')
        result = replay_dir / (path.stem + '.output.csv')
        write_replay(path, data, baseline, candidate, source, baseline_enabled)
        with (replay_dir / (path.stem + '.log')).open('w') as log:
            subprocess.run(command + [str(source), str(result)], check=True, stdout=log,
                           stderr=subprocess.STDOUT, timeout=1200)
        with result.open() as f:
            rows = list(csv.DictReader(f))
        all_rows.extend(rows)
    report = compare_navigation(all_rows)
    report['limitations'] = [
        'Uses actual LiveNavigation/NavigationFusion ESKF sources, with GPS hidden for 30 seconds every 120 seconds after 60 seconds of warmup. Candidate activity counts accepted ESKF corrections.',
        'DRLOG has no Android gravity, compass uncertainty or GPS bearing. Replay estimates gravity from quiet acceleration, compass from raw magnetometer and causal course from pre-outage positions.',
        'Map matching is not replayed. No hidden GPS positions or speeds enter the navigation engine.',
        'GPS references are noisy, not surveyed truth. Controlled physical phone testing including map matching is mandatory.']
    return report
