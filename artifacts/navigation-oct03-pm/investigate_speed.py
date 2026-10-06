"""Read-only diagnostics. Does not fit/select/promote any models or alter quality gates."""
import os, sys, json, csv, hashlib
from pathlib import Path
os.environ['TF_CPP_MIN_LOG_LEVEL']='2'
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'ml'))
import numpy as np
from phone_learning import read_session,build_dataset,metrics
from laptop_agent.evaluate import predictions

def invariant(w):
    a=w[:,:,:3];g=w[:,:,3:];ac=a-a.mean(1,keepdims=True)
    return np.stack([np.sqrt((a*a).sum(-1)+1e-6)-9.80665,np.sqrt((g*g).sum(-1)+1e-6),np.sqrt((ac*ac).sum(-1)+1e-6),(a*g).sum(-1)/9.80665],-1)
def summary(y):
    return {'count':len(y),'quantiles_mps':np.quantile(y,[0,.1,.5,.9,1]).tolist(),'bands':{str((lo,hi)):int(((y>=lo)&(y<hi)).sum()) for lo,hi in [(0,.5),(.5,1),(1,5),(5,15),(15,80)]}}
paths=list((ROOT/'artifacts/refinement-oct03/phone/drives').glob('*.drlog'))+list((ROOT/'artifacts/refinement-oct03/cloud').rglob('*.drlog'))
unique={p.stem:p for p in paths};paths=list(unique.values());rows=[];ws=[];ys=[];ss=[];lags=[]
for p in sorted(paths):
    w,y,t,a=read_session(p)
    if not len(y):rows.append(a);continue
    with p.with_suffix('.quality.csv').open() as f:q={int(r['t_ns']):int(r['fix_t_ns']) for r in csv.DictReader(f)}
    lag=np.array([(int(z)-q[int(z)])*1e-9 for z in t]);lags.extend(lag.tolist())
    bucket=int(hashlib.sha256(p.stem.encode()).hexdigest()[:8],16)%10
    a.update(summary(y));a['split']='train' if bucket<6 else 'validation' if bucket<8 else 'test';a['lag_seconds']=np.quantile(lag,[0,.5,.95,1]).tolist()
    rows.append(a);ws.append(w);ys.append(y);ss.extend([p.stem]*len(y))
w=np.concatenate(ws);y=np.concatenate(ys);ss=np.array(ss)
pred=predictions(ROOT/'android/app/src/main/assets/models/speed.tflite',w)
for row in rows:
    if 'session' not in row or 'count' not in row:continue
    m=ss==row['session'];row['bundled_error_mps']=metrics(y[m],pred[m]);row['input_invariant_mean']=invariant(w[m]).mean((0,1)).tolist()
d,r=build_dataset(paths)
source=np.load(ROOT/'artifacts/ml/windows-v2.npz');sw=source['w'][source['split']=='train'];sy=source['speed'][source['split']=='train']
def stats(w):
    z=invariant(w).reshape(-1,4)
    return {'mean':z.mean(0).tolist(),'std':z.std(0).tolist(),'quantiles':np.quantile(z,[.01,.5,.99],axis=0).tolist()}
report={'scope':'Diagnostic reused cohort, not new blind validation; no models trained or promoted','sessions':rows,'combined':summary(y),'combined_bundled_error_mps':metrics(y,pred),'phone_features':stats(w),'iovnbd_train_features':stats(sw),'iovnbd_train_speed':summary(sy),'production_training_blockers':r['training_blockers'],'production_splits':{split:summary(d['speed'][d['split']==split]) if (d['split']==split).any() else {'count':0} for split in ('train','validation','test')}}
(Path(__file__).parent/'speed-investigation.json').write_text(json.dumps(report,indent=2))
np.savez_compressed(Path(__file__).parent/'speed-diagnostic-windows.npz',w=w,speed=y,session=ss,prediction=pred)
print(json.dumps({k:v for k,v in report.items() if k!='sessions'},indent=2))
