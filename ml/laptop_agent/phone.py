"""Isolated candidate inference on an attached physical Android phone."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
from .common import ROOT, read_json, save_json, sha
from .release import validate_candidate


def device():
    adb = Path(os.environ.get('LOCALAPPDATA', str(Path.home() / 'AppData/Local'))) / 'Android/Sdk/platform-tools/adb.exe'
    if not adb.exists():
        raise RuntimeError('Android platform-tools were not found. Install them in Android Studio.')
    result = subprocess.run([str(adb), 'devices'], check=True, capture_output=True, text=True)
    devices = [r.split()[0] for r in result.stdout.splitlines()[1:] if r.strip().endswith('\tdevice')]
    if len(devices) != 1:
        raise RuntimeError('Connect exactly one phone with USB debugging enabled and approve its USB prompt.')
    return [str(adb), '-s', devices[0]], devices[0]


def phone_test(root):
    report = validate_candidate(root)
    adb, serial = device()
    hardware = subprocess.run(adb + ['shell','getprop','ro.hardware'], check=True, capture_output=True, text=True).stdout.strip()
    if serial.startswith('emulator-') or any(s in hardware for s in ('ranchu', 'goldfish')):
        raise ValueError('Approval requires a physical phone, not an emulator')
    apk = ROOT / 'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    if not apk.exists():
        raise RuntimeError('Prepare Android tests using the launcher, then retry Phone test.')
    # Installing only the test APK leaves the navigation application and its models intact.
    subprocess.run(adb + ['install', '-r', str(apk)], check=True)
    destination = '/data/local/tmp/offroute-laptop-candidate'
    subprocess.run(adb + ['shell','mkdir','-p',destination], check=True)
    private = 'files/laptop-candidate'
    subprocess.run(adb + ['shell','run-as','com.idr.navigator','mkdir','-p',private], check=True)
    for local, name in [(root / 'speed-candidate.tflite','candidate.tflite'), (root / 'phone-parity.bin','phone-parity.bin')]:
        subprocess.run(adb + ['push',str(local),destination+'/'+name], check=True)
        subprocess.run(adb + ['shell','run-as','com.idr.navigator','cp',destination+'/'+name,private+'/'+name], check=True)
    nonce = str(time.time_ns())
    command = adb + ['shell','am','instrument','-w','-r','-e','class','com.idr.navigator.LaptopCandidateTest',
                     '-e','candidate_sha256',report['candidate_sha256'],'-e','nonce',nonce,
                     'com.idr.navigator.test/androidx.test.runner.AndroidJUnitRunner']
    with (root / 'phone-test.log').open('w') as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=300)
    if result.returncode:
        raise RuntimeError('Phone test failed; see ' + str(root / 'phone-test.log'))
    received = subprocess.run(adb + ['exec-out','run-as','com.idr.navigator','cat',private+'/result.json'],
                              check=True,capture_output=True).stdout
    result = json.loads(received)
    if result.get('nonce') != nonce or result.get('candidate_sha256') != report['candidate_sha256'] or result.get('passed') is not True:
        raise ValueError('Phone runtime result is missing, stale, or failed')
    result.update(device=serial, hardware=hardware, report_sha256=sha(root / 'report.json'), tested_at=time.time(),
                  evidence={}, field_test={'passed': False})
    save_json(root / 'phone-test.json', result)
    return {'status':'phone_runtime_passed', 'message':'Inference parity passed. Controlled field evidence is still required before approval.',
            'max_absolute_error_mps':result['max_absolute_error_mps']}


def field_evidence(root, trip, video):
    report = validate_candidate(root)
    result = read_json(root / 'phone-test.json', {})
    if result.get('passed') is not True or result.get('candidate_sha256') != report['candidate_sha256']:
        raise ValueError('Run the physical Phone test first')
    trip, video = Path(trip), Path(video)
    if trip.suffix.lower() != '.drlog' or video.suffix.lower() not in ('.mp4','.webm','.mkv') or not trip.is_file() or not video.is_file():
        raise ValueError('Choose the matching real trip (.drlog) and screen recording (.mp4/.webm/.mkv)')
    from phone_learning import read_session
    metadata = read_json(trip.with_suffix('.training.json'), {})
    if metadata.get('candidateTrialSha256') != report['candidate_sha256']:
        raise ValueError('Use a trip recorded by the separate Candidate Test app with this candidate active')
    windows, _, _, audit = read_session(trip, allow_trial=True)
    if audit.get('rejected_session') or len(windows) < 60:
        raise ValueError('Field trip needs matching .quality.csv and .training.json plus at least 60 reliable GPS fixes')
    if trip.stem in {s for sessions in report['splits'].values() for s in sessions}:
        raise ValueError('Field evidence must be a new physical trip outside all training/evaluation splits')
    print('Runtime parity is not field validation. Confirm only after reviewing a controlled candidate test.')
    print('Candidate SHA-256: ' + report['candidate_sha256'])
    answers = {}
    for key, prompt in [('speed','Candidate speed was independently checked against reliable GPS'),
                        ('stops','Candidate introduced no false movement while stopped'),
                        ('gps_loss','Controlled GPS-loss drift was no worse than the current model'),
                        ('map_matching','Map matching and GPS recovery were reviewed without regressions')]:
        answers[key] = input(prompt + ' [type YES]: ').strip() == 'YES'
    notes = input('Describe the candidate test setup, results, and independent reference: ').strip()
    if not all(answers.values()) or not notes:
        raise ValueError('Field review incomplete; no approval is possible')
    evidence = root / 'evidence'; evidence.mkdir(exist_ok=True)
    files = [trip, trip.with_suffix('.quality.csv'), trip.with_suffix('.training.json'), video]
    digests = {}
    for i, source in enumerate(files):
        name = source.name if source != video else 'screen-recording' + video.suffix.lower()
        shutil.copy2(source, evidence / name); digests[name] = sha(evidence / name)
    result['evidence'] = digests
    result['field_test'] = dict(answers, passed=True, notes=notes, reviewed_at=time.time())
    save_json(root / 'phone-test.json', result)
    return {'status':'field_evidence_recorded', 'message':'Use Approve to review and authorize this exact candidate.'}


def prepare_trial(root):
    report = validate_candidate(root)
    metadata = read_json(root / 'phone-test.json', {})
    if metadata.get('passed') is not True or metadata.get('candidate_sha256') != report['candidate_sha256']:
        raise ValueError('Pass the phone inference test before preparing a field trial')
    assets = ROOT / 'artifacts/laptop-agent/trial-assets'
    (assets / 'models').mkdir(parents=True, exist_ok=True)
    manifest = read_json(ROOT / 'android/app/src/main/assets/models/manifest.json')
    for model in manifest['models']:
        if model['name'] == 'speed':
            model.update(sha256=report['candidate_sha256'], validated=True, placeholder=False)
    shutil.copy2(root / 'speed-candidate.tflite', assets / 'models/speed.tflite')
    save_json(assets / 'models/manifest.json', manifest)
    save_json(assets / 'laptop-trial.json', {'candidate_sha256':report['candidate_sha256']})
    return {'status':'trial_prepared', 'candidate_sha256':report['candidate_sha256']}


def install_trial(root):
    report = validate_candidate(root); adb, serial = device()
    apk = ROOT / 'android/app/build/outputs/apk/laptopTrial/app-laptopTrial.apk'
    import zipfile
    with zipfile.ZipFile(apk) as z:
        import hashlib
        if hashlib.sha256(z.read('assets/models/speed.tflite')).hexdigest() != report['candidate_sha256']:
            raise ValueError('Trial APK does not contain this candidate')
    subprocess.run(adb + ['install','-r',str(apk)], check=True)
    # Reusing the test app across candidates requires replacing its private speed
    # binary too. Stop only the isolated trial app, never the normal application.
    subprocess.run(adb + ['shell','am','force-stop','com.idr.navigator.candidate'],check=True)
    remote='/data/local/tmp/offroute-laptop-candidate/trial-speed.tflite'
    subprocess.run(adb + ['push',str(root / 'speed-candidate.tflite'),remote],check=True)
    subprocess.run(adb + ['shell','run-as','com.idr.navigator.candidate','mkdir','-p','files/models'],check=True)
    subprocess.run(adb + ['shell','run-as','com.idr.navigator.candidate','cp',remote,'files/models/speed.tflite'],check=True)
    subprocess.run(adb + ['shell','am','start','-n','com.idr.navigator.candidate/com.idr.navigator.MainActivity'],check=True)
    save_json(root / 'trial-install.json', {'device':serial,'candidate_sha256':report['candidate_sha256'],'apk_sha256':sha(apk),'at':time.time()})
    return {'status':'trial_installed','message':'Use OFF-ROUTE Candidate Test for the controlled trip. Keep cloud contribution off. The normal app is unchanged.'}


def import_trial(root):
    report = validate_candidate(root); adb, serial = device()
    # Never unpack untrusted tar paths; copy only allowlisted regular UUID files.
    import tarfile
    from .ingest import identity, SUFFIXES
    archive = root / 'trial-import.tar'
    with archive.open('wb') as f:
        subprocess.run(adb + ['exec-out','run-as','com.idr.navigator.candidate','tar','cf','-','files/drives'],
                       check=True,stdout=f,timeout=300)
    folder = root / 'field-trips'; folder.mkdir(exist_ok=True)
    with tarfile.open(archive) as tar:
        for member in tar:
            name=Path(member.name).name
            if not member.isfile() or member.size > 500*1024**2 or name[36:] not in SUFFIXES:
                continue
            try: identity(name[:36])
            except ValueError: continue
            with tar.extractfile(member) as incoming, (folder / name).open('wb') as out:
                shutil.copyfileobj(incoming,out)
            # Preserve recording order; archive iteration order is not trip age.
            os.utime(folder / name, (member.mtime, member.mtime))
    matching=[]
    for trip in folder.glob('*.drlog'):
        meta=read_json(trip.with_suffix('.training.json'),{})
        if meta.get('candidateTrialSha256') == report['candidate_sha256'] and trip.with_suffix('.summary.json').exists():
            matching.append(trip)
    if not matching: raise ValueError('Finish a trip in OFF-ROUTE Candidate Test before importing evidence')
    chosen=max(matching,key=lambda p:p.stat().st_mtime_ns)
    save_json(root / 'field-trip.json', {'path':str(chosen),'device':serial})
    return {'status':'field_trip_imported','path':str(chosen)}
