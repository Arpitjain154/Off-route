"""Reproduce outage ablations from local IO-VNBD and exact Java source snapshots.

Usage: .venv-ml/Scripts/python.exe ml/evaluate_outages.py --label stage-a
No downloads, training, graph mutation or hidden-GNSS input to the estimator.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
BASE = '41670ae7'
ENGINE = 'android/app/src/main/java/com/idr/navigator/engine'


def clock(frame):
    parts = frame.iloc[:, 8].astype(str).str.extract(r'(\d{2}):(\d{2}):(\d{2})[:_\.](\d+)').astype(float)
    t = (parts[0]*3600 + parts[1]*60 + parts[2] + parts[3]/1000).to_numpy()
    return t - t[0]


def number(frame):
    return frame.apply(pd.to_numeric, errors='coerce').to_numpy(float)


def prepare(out):
    manifest = json.loads((ROOT/'data/iovnbd/manifest.json').read_text())
    entries = {r['path']: r for r in manifest['files']}
    recordings = []
    for rel in sorted(entries):
        name = Path(rel).stem
        if not name.startswith('S-') or not ('Driver A' in rel or name.startswith('S-Vfa')):
            continue
        source = ROOT/'data/iovnbd'/rel
        vrel = str(Path(rel).with_name('V-' + Path(rel).name[2:])).replace('\\', '/')
        vehicle = ROOT/'data/iovnbd'/vrel
        for path, key in [(source, rel), (vehicle, vrel)]:
            assert hashlib.sha256(path.read_bytes()).hexdigest() == entries[key]['sha256']
        s = pd.read_csv(source, encoding='latin1'); v = pd.read_csv(vehicle, encoding='latin1')
        st = clock(s); vt = pd.to_numeric(v.iloc[:, 1]).to_numpy(); vt = vt-vt[0]
        order = np.argsort(vt); vt = vt[order]; v = v.iloc[order]
        ix = np.searchsorted(vt, st).clip(0, len(vt)-1); left = np.maximum(0, ix-1)
        ix = np.where(abs(vt[left]-st) < abs(vt[ix]-st), left, ix)
        a = np.column_stack([st, number(s.iloc[:, [9,10,11,15,16,17]]),
                             number(s.iloc[:, [0,1,4,3,5]]), number(v.iloc[ix, [2,3,4]])])
        a[:,14] /= 3.6
        valid = (st <= 600) & (abs(vt[ix]-st) <= .061) & np.isfinite(a).all(axis=1)
        a = a[valid]
        a = a[np.r_[True, np.diff(a[:,0]) > 0]]
        changed = np.r_[True, np.any(np.diff(a[:,[7,8,10,11]],axis=0) != 0,axis=1)]
        # Determine speed units from phone GNSS only, without vehicle labels.
        g = a[changed]; dt = np.diff(g[:,0]); north = np.diff(g[:,7])*111320
        east = np.diff(g[:,8])*111320*np.cos(np.deg2rad(g[1:,7]))
        good = (dt > 0) & (dt < 20) & (g[1:,10] > 3) & (np.hypot(east,north)/dt > 3)
        ratio = float(np.median((np.hypot(east,north)/dt)[good]/g[1:,10][good]))
        # Unit audit is metadata; evaluation must not silently fit a scale to reference speed.
        if not .65 < ratio < 1.4:
            raise ValueError(f'{name}: ambiguous phone speed units ({ratio})')
        np.savez(out/(name+'.npz'), rows=a, changed=changed)
        recordings.append(dict(name=name,split='validation-Vfa' if name.startswith('S-Vfa') else 'regression-S',
                               source=rel,sha256=entries[rel]['sha256'],rows=len(a),speed_unit_ratio=ratio))
    return recordings


def mapping(a, changed, start):
    """Learn frame from phone GNSS strictly before this independent outage."""
    pre = a[:,0] < start
    raw = a[pre]; up = np.median(raw[:,1:4],axis=0); up /= np.linalg.norm(up)
    fixes = np.flatnonzero(pre & changed)
    # Aggregate 10 Hz speed/course tuples over >=1 s for causal heading rates.
    selected=[]
    for index in fixes:
        if not selected or a[index,0]-a[selected[-1],0]>=1:
            selected.append(index)
    fixes=np.asarray(selected,dtype=int)
    gyro, rates, accels, dv = [], [], [], []
    for lo, hi in zip(fixes[:-1], fixes[1:]):
        dt = a[hi,0]-a[lo,0]
        if 1 <= dt <= 20 and min(a[lo,10],a[hi,10]) > 3 and max(a[lo,9],a[hi,9]) <= 15:
            turn = ((a[hi,11]-a[lo,11]+180)%360-180)*np.pi/180/dt
            if abs(turn) < 1:
                gyro.append(a[lo:hi,4:7].mean(axis=0)); rates.append(turn)
                accels.append(a[lo:hi,1:4].mean(axis=0)); dv.append((a[hi,10]-a[lo,10])/dt)
    corr = np.zeros(3); axis = 2; sign = -1.; reliable = False
    forward = np.array([0.,1.,0.]); forward -= up*np.dot(up,forward)
    acceleration_reliable = False
    if len(rates) >= 4 and np.std(rates) > .005:
        g = np.asarray(gyro)
        for k in range(3):
            if np.std(g[:,k]) > .001: corr[k] = np.corrcoef(g[:,k],rates)[0,1]
        axis = int(np.argmax(abs(corr))); reliable = abs(corr[axis]) >= .5
        if reliable: sign = -float(np.sign(corr[axis]))
        else: axis = 2
    if len(dv) >= 4 and np.std(dv) > .1:
        acc = np.asarray(accels); target = np.asarray(dv)
        direction = np.mean((acc-acc.mean(axis=0))*(target-target.mean())[:,None],axis=0)
        direction -= up*np.dot(up,direction)
        if np.linalg.norm(direction) > .01:
            direction /= np.linalg.norm(direction)
            score = np.corrcoef(acc@direction,target)[0,1]
            if score > .4: forward=direction; acceleration_reliable=True
    forward /= np.linalg.norm(forward); right = np.cross(forward,up)
    mapped = a.copy()
    mapped[:,1:4] = a[:,1:4] @ np.column_stack([right,forward,up])
    # Gyro labels are not assumed to share accelerometer axes. Only the learned
    # vertical component is used; planar fixture limitations are reported explicitly.
    mapped[:,4:7] = 0; mapped[:,6] = sign*a[:,4+axis]
    return mapped, dict(start_s=start,gyro_axis=axis,gyro_sign=sign,correlations=corr.tolist(),
                        gyro_reliable=bool(reliable),forward_reliable=acceleration_reliable,
                        calibration_samples=len(rates),last_calibration_s=float(raw[-1,0]))


def compile_java(out, baseline):
    jdk = Path(os.environ.get('JAVA_HOME', str(Path.home()/'.jdks/jbr-21.0.11')))/'bin'
    jars = [p for p in (Path.home()/'.gradle/caches/modules-2/files-2.1/org.ejml').glob('**/*.jar')
            if not p.name.endswith(('-sources.jar','-javadoc.jar'))]
    cp = os.pathsep.join(map(str,jars)); classes=out/('baseline-classes' if baseline else 'candidate-classes')
    classes.mkdir(exist_ok=True)
    source = ROOT/'android/app/src/main/java'
    if baseline:
        source=out/'baseline-src'; dest=source/'com/idr/navigator/engine'; dest.mkdir(parents=True,exist_ok=True)
        files=subprocess.check_output(['git','ls-tree','--name-only',BASE,ENGINE+'/'],cwd=ROOT,text=True).splitlines()
        for name in files:
            if name.endswith('.java'):
                (dest/Path(name).name).write_bytes(subprocess.check_output(['git','show',BASE+':'+name],cwd=ROOT))
    sourcepath=str(source) if baseline else os.pathsep.join([str(ROOT/'artifacts/eval/experimental-src'),str(out/'baseline-src')])
    command=[str(jdk/'javac.exe'),'-cp',cp,'-sourcepath',sourcepath,'-d',str(classes),str(ROOT/'ml/java/OutageEvaluation.java')]
    subprocess.run(command,check=True,capture_output=True)
    return [str(jdk/'java.exe'),'-Xmx512m','-cp',str(classes)+os.pathsep+cp,'OutageEvaluation']


def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--label',default='final')
    parser.add_argument('--prepare-only',action='store_true'); parser.add_argument('--no-ai',action='store_true')
    args=parser.parse_args(); out=ROOT/'artifacts/eval'/args.label; out.mkdir(parents=True,exist_ok=True)
    recordings=prepare(out)
    (out/'recordings.json').write_text(json.dumps(recordings,indent=2))
    if args.prepare_only: return
    commands={'baseline':compile_java(out,True),'candidate':compile_java(out,False)}
    interpreter=None
    if not args.no_ai:
        os.environ['TF_CPP_MIN_LOG_LEVEL']='2'
        import tensorflow as tf
        interpreter=tf.lite.Interpreter(model_path=str(ROOT/'android/app/src/main/assets/models/speed.tflite'),num_threads=1)
        interpreter.allocate_tensors()
        assert tuple(interpreter.get_input_details()[0]['shape']) == (1,300,6)
    metrics=[]; audits=[]; trajectories=[]; replay_cache={}
    for rec in recordings:
        data=np.load(out/(rec['name']+'.npz')); raw=data['rows']; changed=data['changed']
        predictions=np.full(len(raw),np.nan)
        if interpreter is not None:
            previous=-1
            for i in range(29,len(raw)):
                second=int(raw[i,0])
                if second==previous: continue
                previous=second
                value=np.repeat(raw[i-29:i+1,1:7][None].astype('float32'),10,axis=1)
                interpreter.set_tensor(interpreter.get_input_details()[0]['index'],value);interpreter.invoke()
                predictions[i]=float(interpreter.get_tensor(interpreter.get_output_details()[0]['index']).flat[0])
        for start in [60.,300.]:
            for duration in [30,60,120]:
                if raw[-1,0] < start+duration: continue
                mapped,audit=mapping(raw,changed,start); audits.append(dict(recording=rec['name'],duration_s=duration,**audit))
                take=(raw[:,0]>=max(0,start-60))&(raw[:,0]<=start+120)
                fixture=np.column_stack([mapped,predictions,changed.astype(int)])[take]
                window=fixture[(fixture[:,0]>=start)&(fixture[:,0]<=start+duration)]; reference=window[:,12:14]
                lat=reference[0,0]; scale=np.array([111320,111320*np.cos(np.deg2rad(lat))])
                truth=(reference-reference[0])*scale
                distance=float(np.linalg.norm(np.diff(truth,axis=0),axis=1).sum())
                if len(window)<duration*8 or distance<1: continue
                stem=f'{rec["name"]}-{int(start)}-{duration}'; path=out/(stem+'-input.csv')
                np.savetxt(path,fixture,delimiter=',',header='seconds,ax,ay,az,gx,gy,gz,lat,lon,accuracy,speed,bearing,ref_lat,ref_lon,ref_speed,ai_speed,new_fix',comments='')
                anchor=fixture[(fixture[:,0]<start)&(fixture[:,16]==1)][-1]
                elapsed=window[:,0]-window[0,0]; h=np.deg2rad(anchor[11])
                constant=np.column_stack([elapsed*anchor[10]*np.cos(h),elapsed*anchor[10]*np.sin(h)])
                runs=[('last GNSS speed + constant heading',constant,np.full(len(window),anchor[10]),0.)]
                for mode,ai in [('baseline',False),('speed',False),('gyro',False),('candidate',False),('candidate',True)]:
                    if ai and args.no_ai: continue
                    target=out/(stem+'-'+mode+('-ai' if ai else '')+'.csv')
                    key=(rec['name'],start,mode,ai)
                    if key not in replay_cache:
                        subprocess.run(commands['baseline' if mode=='baseline' else 'candidate']+[str(path),str(target),mode,str(ai).lower(),str(start)],check=True,capture_output=True,timeout=180)
                        replay_cache[key]=pd.read_csv(target)
                    estimated=replay_cache[key];estimated=estimated[estimated.seconds<=start+duration+1e-8]
                    assert len(estimated)==len(window)
                    points=estimated[['lat','lon']].to_numpy()
                    points=(points-points[0])*scale
                    title={'baseline':'ESKF baseline commit','speed':'ESKF + A (rejected)','gyro':'ESKF + C only','candidate':'ESKF + A+C (experimental; B not evaluated)'}[mode]
                    if ai:title+=' + AI'
                    runs.append((title,points,estimated.speed.to_numpy(),float(estimated.ai_used.mean())))
                for title,points,speed,used in runs:
                    error=np.linalg.norm(points-truth,axis=1); speed_error=speed-window[:,14]
                    metrics.append(dict(split=rec['split'],recording=rec['name'],start_s=start,duration_s=duration,ablation=title,
                        distance_m=distance,mean_reference_kmh=float(window[:,14].mean()*3.6),endpoint_drift_pct=float(error[-1]/distance*100),
                        ate_m=float(np.sqrt(np.mean(error**2))),speed_mae_mps=float(np.mean(abs(speed_error))),
                        speed_rmse_mps=float(np.sqrt(np.mean(speed_error**2))),ai_used_fraction=used))
                    for t,ref,est in zip(window[:,0],truth,points):
                        trajectories.append((rec['split'],rec['name'],start,duration,title,t,ref[1],ref[0],est[1],est[0]))
                print(stem, 'complete',flush=True)
    frame=pd.DataFrame(metrics);frame.to_csv(out/'windows.csv',index=False)
    pd.DataFrame(trajectories,columns=['split','recording','start_s','duration_s','ablation','seconds','reference_east','reference_north','estimated_east','estimated_north']).to_csv(out/'trajectories.csv',index=False)
    (out/'axis-calibration.json').write_text(json.dumps(audits,indent=2))
    summaries=[]
    for subset in ['all','>=40 km/h']:
        selected=frame if subset=='all' else frame[frame.mean_reference_kmh>=40]
        for (split,duration,title),group in selected.groupby(['split','duration_s','ablation']):
            summaries.append(dict(split=split,subset=subset,duration_s=duration,ablation=title,windows=len(group),
                median_drift_pct=group.endpoint_drift_pct.median(),mean_drift_pct=group.endpoint_drift_pct.mean(),
                under_10_pct=float((group.endpoint_drift_pct<10).mean()*100),ate_m=group.ate_m.mean(),
                speed_mae_mps=group.speed_mae_mps.mean(),speed_rmse_mps=group.speed_rmse_mps.mean()))
    summary=pd.DataFrame(summaries);summary.to_csv(out/'summary.csv',index=False)
    pivot=frame.pivot(index=['split','recording','start_s','duration_s'],columns='ablation',values='endpoint_drift_pct')
    baseline=pivot['ESKF baseline commit'];combined='ESKF + A+C (experimental; B not evaluated)'
    decision={'windows':len(pivot),'stage_a_regressed_windows':int((pivot['ESKF + A (rejected)']>baseline+1e-6).sum()),
              'stage_c_regressed_windows':int((pivot['ESKF + C only']>baseline+1e-6).sum()),
              'ai_regressed_windows':None if args.no_ai else int((pivot[combined+' + AI']>pivot[combined]+1e-6).sum()),
              'production_promoted':False,'map_matching':'not evaluated'}
    (out/'decisions.json').write_text(json.dumps(decision,indent=2))
    lines=['# OFFROUTE outage evaluation','',f'Baseline source: `{BASE}`. Run: `{sys.executable} ml/evaluate_outages.py --label {args.label}`.',
        '', '| Split | Subset | Seconds | Ablation | N | Median drift % | Mean drift % | Under 10% (%) | ATE m | Speed MAE m/s | Speed RMSE m/s |',
        '|---|---|---:|---|---:|---:|---:|---:|---:|---:|---:|']
    for r in summaries:
        lines.append('| '+ ' | '.join(str(r[k]) if k in ['split','subset','ablation','duration_s','windows'] else f'{r[k]:.3f}' for k in r)+' |')
    lines += ['', 'Map matching: **not evaluated**. Bundled graph covers Madhya Pradesh; no Coventry graph is supplied.', '',
        'Limitations: Vfa is validation; S* is a previously reused regression set, not a blind test. No training data is used here. '
        'First 600 seconds per local recording; independent outages start at 60 and 300 seconds, with 60 seconds of warmup. '
        'Phone/vehicle elapsed clocks are aligned as in the existing harness; vehicle GNSS is a noisy scoring reference, never estimator input. '
        'Paths are translated to zero at the first outage sample (relative ATE; no rotation or scale fitting). '
        'Speed errors use vehicle GNSS speed. ATE and speed metrics in the table are means of per-window metrics. '
        'The high-speed subset uses mean reference speed >=40 km/h. Near-stationary distance under 1 m is excluded. '
        'Frame calibration uses only pre-outage phone GNSS and raw accelerometer/gyro; constant GRAVITY columns are ignored. '
        'Planar replay suppresses unidentifiable transverse gyro components. Low-correlation mappings are flagged, not claimed calibrated. '
        'The 10 Hz recording is held at 100 Hz for the existing filter; output is 10 Hz. This is not a measured mobile performance result. '
        'AI uses the unchanged 300x6 -> 1 bundled model, with experimental variance 25 (m/s)^2 and trust 0.10; it is not validated or enabled live.']
    lines += ['', 'Disposition: A and C are evaluation-only rejected snapshots; no live engine change is retained. '
              'Stage B is not included in numeric ablations because no UK graph is available; its attempted live wiring was reverted after an edge source-list incompatibility. '
              'Earlier stage-a results used an overly restrictive calibration interval gate and are superseded by this final run. '
              'Neither the aggregate gains nor experimental AI establish the <10% benchmark. Optional latency work was skipped.']
    (out/'report.md').write_text('\n'.join(lines),encoding='utf-8')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    track=pd.DataFrame(trajectories,columns=['split','recording','start_s','duration_s','ablation','seconds','rx','ry','ex','ey'])
    fig,axes=plt.subplots(2,3,figsize=(15,9),layout='constrained')
    for row,split in enumerate(['validation-Vfa','regression-S']):
        for col,duration in enumerate([30,60,120]):
            d=track[(track.split==split)&(track.duration_s==duration)]
            if d.empty: continue
            name=sorted(d.recording.unique())[0];d=d[(d.recording==name)&(d.start_s==d.start_s.min())]
            ax=axes[row,col];first=True
            for title,g in d.groupby('ablation',sort=False):
                if first: ax.plot(g.rx,g.ry,'k',linewidth=2,label='Vehicle GNSS');first=False
                ax.plot(g.ex,g.ey,label=title)
            ax.set_title(f'{name}: {duration}s (first eligible window)');ax.set_aspect('equal',adjustable='datalim');ax.grid(alpha=.25)
            ax.set_xlabel('East (m)');ax.set_ylabel('North (m)')
    handles,labels=axes[0,0].get_legend_handles_labels();fig.legend(handles,labels,loc='outside lower center',ncol=2,fontsize=8)
    fig.suptitle('Reference vs estimated — pre-outage frame calibration; no road graph')
    fig.savefig(out/'reference-vs-estimated.png',dpi=150);plt.close(fig)
    print(summary.to_string(index=False),flush=True)


if __name__=='__main__':main()
