"""Conservative ingestion and candidate training from opted-in phone navigation logs.
No deployed weights or manifests are modified. Splits are whole sessions.
"""
import argparse,csv,hashlib,json
from pathlib import Path
import numpy as np
from read_drlog import load


def read_session(path,allow_trial=False):
    path=Path(path);audit={'file':path.name,'rejections':{}}
    def empty(reason):
        audit['rejected_session']=reason
        return np.empty((0,300,6),np.float32),np.empty(0),np.empty(0,np.int64),audit
    if not path.with_suffix('.training.json').exists() or not path.with_suffix('.quality.csv').exists():return empty('missing_quality_metadata')
    try:
        provenance=json.loads(path.with_suffix('.training.json').read_text())
        if provenance.get('source')!='device':return empty('not_physical_device')
        if provenance.get('candidateTrialSha256') and not allow_trial:return empty('candidate_trial_excluded')
        meta,records=load(path);records=np.array(records) # release the mmap before callers archive/move a session
        if provenance.get('schemaVersion')!=1 or provenance.get('sessionId')!=meta['session_id'] or path.stem!=meta['session_id']:return empty('identity_mismatch')
        audit['session']=meta['session_id']
        with path.with_suffix('.quality.csv').open() as f:
            quality={}
            for r in csv.DictReader(f):
                t=int(r['t_ns'])
                if t in quality:return empty('duplicate_quality_timestamp')
                quality[t]=(int(r['fix_t_ns']),float(r['speed_accuracy_mps']),r['gyro_fresh']=='1')
    except (OSError,ValueError,KeyError,IndexError):return empty('invalid_or_incomplete_recording')
    if len(records)<300:return empty('too_short')
    ts=records['t_ns'];imu=np.column_stack([records[a] for a in ('ax','ay','az','gx','gy','gz')])
    if np.any(np.diff(ts)<=0):return empty('non_monotonic_time')
    windows=[];labels=[];times=[];seen=set()
    def reject(reason):audit['rejections'][reason]=audit['rejections'].get(reason,0)+1
    for i,row in enumerate(records):
        q=quality.get(int(ts[i]))
        if q is None:continue
        fix,error,gyro=q
        if fix<=0 or fix in seen:continue
        # Only the first appearance of a new GNSS observation creates one candidate label.
        seen.add(fix)
        if not (0<=int(ts[i])-fix<=1_000_000_000 and np.isfinite(error) and 0<=error<=1 and gyro and 0<row['horiz_acc']<=15 and 0<=row['speed_mps']<=80 and row['flags']&1):reject('label_quality');continue
        end=int(ts[i]);start=end-2_990_000_000
        left=int(np.searchsorted(ts,start,side='right'))-1
        if left<0:reject('short_history');continue
        segment=ts[left:i+1]
        if len(segment)<2 or np.max(np.diff(segment))>100_000_000:reject('imu_gap');continue
        if not np.isfinite(imu[left:i+1]).all():reject('invalid_imu');continue
        if any(int(t) not in quality or not quality[int(t)][2] for t in segment):reject('missing_sensor_quality');continue
        grid=np.arange(300,dtype=np.int64)*10_000_000
        # Relative integer times avoid precision loss in long-running Android clocks.
        relative=segment-start
        w=np.column_stack([np.interp(grid,relative,imu[left:i+1,k]) for k in range(6)]).astype(np.float32)
        w=np.repeat(w.reshape(30,10,6).mean(axis=1),10,axis=0)
        windows.append(w);labels.append(float(row['speed_mps']));times.append(end)
    audit['accepted_fixes']=len(windows)
    return np.asarray(windows,np.float32).reshape(-1,300,6),np.asarray(labels,np.float32),np.asarray(times,np.int64),audit


def build_dataset(paths,min_session_fixes=60):
    all_w=[];all_y=[];all_t=[];sessions=[];splits=[];seen=set();report={'sessions':[]}
    for path in sorted(map(Path,paths)):
        w,y,t,a=read_session(path);sid=a.get('session',path.stem)
        if sid in seen:a['rejected_session']='duplicate_session';w=w[:0]
        seen.add(sid)
        if len(w)<min_session_fixes and 'rejected_session' not in a:a['rejected_session']='insufficient_fixes';w=w[:0]
        report['sessions'].append(a)
        if not len(w):continue
        bucket=int(hashlib.sha256(sid.encode()).hexdigest()[:8],16)%10
        split='train' if bucket<6 else 'validation' if bucket<8 else 'test'
        all_w.append(w);all_y.append(y);all_t.append(t);sessions.extend([sid]*len(w));splits.extend([split]*len(w))
    d={'w':np.concatenate(all_w) if all_w else np.empty((0,300,6),np.float32),'speed':np.concatenate(all_y) if all_y else np.empty(0),'t_ns':np.concatenate(all_t) if all_t else np.empty(0,np.int64),'session':np.asarray(sessions),'split':np.asarray(splits)}
    report['accepted_windows']=len(d['w']);report['training_blockers']=training_blockers(d)
    return d,report


def training_blockers(d):
    reasons=[]
    if len(set(d['session']))<5:reasons.append('Need at least five independent sessions')
    for split in ('train','validation','test'):
        mask=d['split']==split
        if mask.sum()<100:reasons.append(f'Need at least 100 quality fixes in {split}')
        if mask.any() and np.ptp(d['speed'][mask])<5:reasons.append(f'Insufficient speed diversity in {split}')
    if len(d['speed']) and not np.isfinite(d['speed']).all():reasons.append('Non-finite labels')
    return reasons


def fit_ridge(x,y,alpha=1):
    mean=x.mean(0);scale=np.maximum(x.std(0),1e-6);z=(x-mean)/scale;ymean=float(np.mean(y))
    weights=np.linalg.solve(z.T@z+np.eye(z.shape[1])*alpha,z.T@(y-ymean))
    return {'mean':mean,'scale':scale,'weights':weights,'bias':np.asarray(ymean)}


def predict_ridge(model,x):return ((x-model['mean'])/model['scale'])@model['weights']+model['bias']


def metrics(y,p):return {'rmse':float(np.sqrt(np.mean((p-y)**2))),'mae':float(np.mean(abs(p-y))),'p95':float(np.quantile(abs(p-y),.95))}

def promotion_blockers(y,candidate,baseline):
    if len(y)==0 or not np.isfinite(candidate).all():return ['Invalid candidate predictions']
    c,b=metrics(y,candidate),metrics(y,baseline);reasons=[]
    if c['rmse']>=b['rmse']*.95:reasons.append('Less than 5% held-out RMSE improvement')
    if c['p95']>b['p95']:reasons.append('Tail error regression')
    for lo,hi in ((0,1),(1,5),(5,15),(15,80)):
        m=(y>=lo)&(y<hi)
        if m.sum()>=10 and metrics(y[m],candidate[m])['mae']>metrics(y[m],baseline[m])['mae']+.1:reasons.append(f'Speed-band regression {lo}-{hi} m/s')
    return reasons


def train(d,baseline_path,out):
    blockers=training_blockers(d)
    if blockers:return {'status':'blocked','reasons':blockers,'promoted':False}
    import tensorflow as tf
    interpreter=tf.lite.Interpreter(model_path=str(baseline_path));interpreter.allocate_tensors();inp=interpreter.get_input_details()[0];output=interpreter.get_output_details()[0]
    if tuple(inp['shape'])!=(1,300,6):raise ValueError('Baseline tensor contract mismatch')
    base=[]
    for w in d['w']:
        interpreter.set_tensor(inp['index'],w[None].astype(inp['dtype']));interpreter.invoke();base.append(float(interpreter.get_tensor(output['index']).ravel()[0]))
    base=np.asarray(base);x=d['w'].reshape(-1,30,10,6).mean(2).reshape(-1,180);a=d['split']=='train';v=d['split']=='validation';t=d['split']=='test'
    # Tune regularization on validation only; final test is never used to fit/select coefficients.
    choices=[fit_ridge(x[a],d['speed'][a],alpha) for alpha in (1,10,100,1000)]
    model=min(choices,key=lambda m:metrics(d['speed'][v],predict_ridge(m,x[v]))['rmse']);prediction=predict_ridge(model,x)
    report={'status':'candidate_only','promoted':False,'validation':metrics(d['speed'][v],prediction[v]),'test':metrics(d['speed'][t],prediction[t]),'baseline_test':metrics(d['speed'][t],base[t]),'reasons':promotion_blockers(d['speed'][t],prediction[t],base[t])+['Controlled phone/outage validation required before deployment']}
    np.savez(out/'candidate-weights.npz',**model)
    inputs=tf.keras.Input(batch_shape=(1,300,6));z=tf.keras.layers.AveragePooling1D(10)(inputs);z=tf.keras.layers.Flatten()(z);dense=tf.keras.layers.Dense(1);net=tf.keras.Model(inputs,dense(z));weight=model['weights']/model['scale'];bias=model['bias']-np.dot(model['mean'],weight);dense.set_weights([weight[:,None].astype(np.float32),np.array([bias],np.float32)])
    converter=tf.lite.TFLiteConverter.from_keras_model(net);(out/'speed-candidate.tflite').write_bytes(converter.convert())
    return report


def main():
    parser=argparse.ArgumentParser();parser.add_argument('logs',type=Path);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--train',action='store_true');parser.add_argument('--baseline',type=Path,default=Path('android/app/src/main/assets/models/speed.tflite'));args=parser.parse_args()
    args.out.mkdir(parents=True,exist_ok=True);d,r=build_dataset(args.logs.rglob('*.drlog'));np.savez_compressed(args.out/'phone-dataset.npz',**d)
    if args.train:r['candidate']=train(d,args.baseline,args.out)
    (args.out/'phone-learning-report.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
if __name__=='__main__':main()
