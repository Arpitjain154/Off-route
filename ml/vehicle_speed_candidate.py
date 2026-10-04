"""Research-only phone-speed candidate. Never modifies deployed assets or validation flags.
Uses rotation-invariant IMU statistics and whole-journey cross-validation within TRAIN.
The existing production training/promotion gates remain authoritative.
"""
import argparse,json,hashlib
from pathlib import Path
import numpy as np
from phone_learning import read_session,build_dataset,fit_ridge,predict_ridge,metrics,promotion_blockers

def features(w):
    w=np.asarray(w,dtype=np.float64)
    a=w[:,:,:3];g=w[:,:,3:];centered=a-a.mean(axis=1,keepdims=True)
    z=np.stack((np.sqrt(np.sum(a*a,axis=2)+1e-6)-9.80665,
                np.sqrt(np.sum(g*g,axis=2)+1e-6),
                np.sqrt(np.sum(centered*centered,axis=2)+1e-6),
                np.sum(a*g,axis=2)/9.80665),axis=2)
    return np.concatenate((z.mean(1),z.std(1),z.max(1),z[:,-50:].mean(1)-z[:,:50].mean(1),np.abs(np.diff(z,axis=1)).mean(1)),axis=1)

def export(model,path):
    import tensorflow as tf
    class Candidate(tf.Module):
        @tf.function(input_signature=[tf.TensorSpec([1,300,6],tf.float32)])
        def __call__(self,w):
            a=w[:,:,:3];g=w[:,:,3:];centered=a-tf.reduce_mean(a,1,keepdims=True)
            z=tf.stack((tf.sqrt(tf.reduce_sum(a*a,2)+1e-6)-9.80665,tf.sqrt(tf.reduce_sum(g*g,2)+1e-6),tf.sqrt(tf.reduce_sum(centered*centered,2)+1e-6),tf.reduce_sum(a*g,2)/9.80665),2)
            mean=tf.reduce_mean(z,1)
            x=tf.concat((mean,tf.sqrt(tf.reduce_mean(tf.square(z-mean[:,None,:]),1)),tf.reduce_max(z,1),tf.reduce_mean(z[:,-50:],1)-tf.reduce_mean(z[:,:50],1),tf.reduce_mean(tf.abs(z[:,1:]-z[:,:-1]),1)),1)
            weights=tf.constant((model['weights']/model['scale']).astype(np.float32))
            bias=tf.constant(float(model['bias']-np.dot(model['mean'],model['weights']/model['scale'])),tf.float32)
            return tf.reshape(tf.clip_by_value(tf.reduce_sum(x*weights,1)+bias,0.,80.),[1,1])
    candidate=Candidate();converter=tf.lite.TFLiteConverter.from_concrete_functions([candidate.__call__.get_concrete_function()],candidate)
    path.write_bytes(converter.convert())

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--prior',type=Path,required=True);p.add_argument('--new',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    inventory=json.loads(a.prior.read_text());paths=[p for d in inventory['verified'] for p in Path(d).glob('*.drlog')]
    # Local files take precedence over their byte-identical cloud copies; identity is deduplicated.
    paths=list({p.stem:p for p in paths+list(Path('artifacts/refinement-oct03/phone/drives').glob('*.drlog'))}.values())
    fresh=sorted(a.new.glob('*.drlog'));known={p.stem for p in paths};fresh=[p for p in fresh if p.stem not in known]
    d,quality=build_dataset(paths);train=d['split']=='train';x=features(d['w']);groups=sorted(set(d['session'][train]))
    if len(groups)<3:raise ValueError('At least three training journeys needed for grouped cross-validation')
    def pred(m,x):return np.clip(predict_ridge(m,x),0,80)
    scores=[]
    for alpha in (1.,10.,100.,1000.):
        folds=[]
        for group in groups:
            fit=train&(d['session']!=group);held=train&(d['session']==group)
            m=fit_ridge(x[fit],d['speed'][fit],alpha);folds.append(metrics(d['speed'][held],pred(m,x[held]))['rmse'])
        scores.append({'alpha':alpha,'mean_journey_rmse':float(np.mean(folds))})
    alpha=min(scores,key=lambda r:r['mean_journey_rmse'])['alpha'];model=fit_ridge(x[train],d['speed'][train],alpha)
    export(model,a.out/'speed-research-candidate.tflite');np.savez(a.out/'weights.npz',**model)
    from laptop_agent.evaluate import predictions,compare_speed
    baseline=Path('android/app/src/main/assets/models/speed.tflite')
    report={'status':'research_only','promoted':False,'input':'IMU only; rotation-invariant statistics','production_blockers':quality['training_blockers'],'train_sessions':groups,'train_fixes':int(train.sum()),'selection':'leave-one-journey-out using training journeys only','cv':scores,'chosen_alpha':alpha,'evaluations':[]}
    # Fresh supplied trip is evaluated only after fitting and selection, never used to tune.
    for path in fresh:
        w,y,t,audit=read_session(path)
        if not len(y):report['evaluations'].append(audit);continue
        expected=pred(model,features(w));actual=predictions(a.out/'speed-research-candidate.tflite',w)
        delta=float(np.max(np.abs(actual-expected)))
        if delta>.001:raise ValueError('Export parity error '+str(delta))
        base=predictions(baseline,w);comparison=compare_speed(y,base,actual)
        report['evaluations'].append({'session':path.stem,'quality':audit,'export_max_abs_error_mps':delta,**comparison})
    held=d['split']=='test'
    if held.any():report['reused_test_diagnostic']=compare_speed(d['speed'][held],predictions(baseline,d['w'][held]),predictions(a.out/'speed-research-candidate.tflite',d['w'][held]))
    report['deployment_blockers']=list(dict.fromkeys(quality['training_blockers']+[b for e in report['evaluations'] for b in e.get('blockers',[])]+report.get('reused_test_diagnostic',{}).get('blockers',[])))+['Independent phone/outage validation required; GPS-denied lane-level accuracy is not established']
    (a.out/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
if __name__=='__main__':main()
