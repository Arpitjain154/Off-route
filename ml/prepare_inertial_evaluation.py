"""Exact bundled TFLite inference on IO-VNBD Driver A replay; reference used only for evaluation.
No training or parameter selection uses this reused regression split.
"""
import os
os.environ['TF_CPP_MIN_LOG_LEVEL']='2'
from pathlib import Path
import json,hashlib
import numpy as np
import pandas as pd
import tensorflow as tf
from train_iovnbd import features
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'artifacts/qa-1.5/iovnbd';OUT.mkdir(parents=True,exist_ok=True)
manifest=json.loads((ROOT/'data/fusion-replay/manifest.json').read_text())
models={}
for name in ['speed','motion','gps-health']:
    path=ROOT/'android/app/src/main/assets/models'/f'{name}.tflite'
    model=tf.lite.Interpreter(model_path=str(path),num_threads=2);model.allocate_tensors();models[name]=model
def infer(name,value):
    m=models[name];m.set_tensor(m.get_input_details()[0]['index'],np.asarray(value,dtype='float32'));m.invoke();return m.get_tensor(m.get_output_details()[0]['index'])[0]
report={'dataset':'IO-VNBD','revision':manifest['revision'],'recordings':[],
        'limitations':manifest['limitations']+['Compass is reconstructed from recorded gravity and magnetometer with Android ENU equations; no reference-trajectory heading is supplied to the estimator.',
        'Original gyro columns are used as sensor x/y/z; ambiguous yaw/pitch/roll labels remain a source-frame uncertainty.',
        'Driver A was evaluated previously and is a regression set, not new blind validation.']}
for recording in manifest['recordings']:
    source=ROOT/'data/iovnbd'/recording['source'];assert hashlib.sha256(source.read_bytes()).hexdigest()==recording['source_sha256']
    s=pd.read_csv(source,encoding='latin1');r=np.loadtxt(ROOT/'data/fusion-replay'/recording['fixture'],delimiter=',',skiprows=1)
    clock=s.iloc[:,8].astype(str).str.extract(r'(\d{2}):(\d{2}):(\d{2})[:_\.](\d+)').astype(float)
    t=(clock[0]*3600+clock[1]*60+clock[2]+clock[3]/1000).to_numpy();t=t-t[0]
    ix=np.searchsorted(t,r[:,0]).clip(0,len(t)-1);left=np.maximum(0,ix-1);ix=np.where(abs(t[left]-r[:,0])<abs(t[ix]-r[:,0]),left,ix)
    imu=s.iloc[:,[9,10,11,15,16,17]].apply(pd.to_numeric,errors='coerce').to_numpy(dtype='float32')
    g=s.iloc[ix,12:15].to_numpy(dtype=float);mag=s.iloc[ix,18:21].to_numpy(dtype=float)
    up=g/np.maximum(1,np.linalg.norm(g,axis=1,keepdims=True));east=np.cross(mag,up);east/=np.maximum(1e-6,np.linalg.norm(east,axis=1,keepdims=True));north=np.cross(up,east)
    axis=np.tile([0.,1.,0.],(len(r),1));axis[np.abs(up[:,1])>.85]=[0,0,-1]
    compass=np.degrees(np.arctan2((east*axis).sum(1),(north*axis).sum(1)))%360
    predictions=np.full((len(r),9),np.nan);last=-1
    for j,index in enumerate(ix):
        if index<29 or int(r[j,0])==last:continue
        w=imu[index-29:index+1];
        if not np.isfinite(w).all():continue
        last=int(r[j,0]);speed=infer('speed',np.repeat(w[None],10,axis=1))[0];motion=infer('motion',features(w[None]));
        # Missing receiver fields remain missing/masked, never synthesized as a healthy label.
        sat=float(str(s.iloc[index,6]).split()[0]) if str(s.iloc[index,6]).split()[0].isdigit() else 0
        hx=np.array([[sat,0,r[j,9],1,0,0,0,0,0,0]],dtype='float32');health=infer('gps-health',hx)
        predictions[j]=np.r_[speed,motion,health]
    target=OUT/recording['fixture'];np.savetxt(target,np.column_stack([r,compass,predictions]),delimiter=',',fmt='%.9f',
        header='seconds,ax,ay,az,gx,gy,gz,phone_lat,phone_lon,accuracy,speed,bearing,reference_lat,reference_lon,gravity_x,gravity_y,gravity_z,phone_compass,ai_speed,motion0,motion1,motion2,motion3,motion4,health0,health1,health2',comments='')
    report['recordings'].append({'file':target.name,'rows':len(r),'inferences':int(np.isfinite(predictions[:,0]).sum()),'source_sha256':recording['source_sha256']});print(report['recordings'][-1],flush=True)
(OUT/'manifest.json').write_text(json.dumps(report,indent=2))
