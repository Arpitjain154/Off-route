"""Reproducible IO-VNBD training. Real measurements, drive-disjoint evaluation, TFLite export."""
import os
os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL', '2')
os.environ.setdefault('TF_ENABLE_ONEDNN_OPTS', '0')
import json, hashlib, re, random
from pathlib import Path
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import mean_absolute_error, mean_squared_error, balanced_accuracy_score, confusion_matrix

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'data/iovnbd'
OUT = ROOT / 'artifacts/ml'
OUT.mkdir(parents=True, exist_ok=True)
SEED = 42
random.seed(SEED); np.random.seed(SEED); tf.keras.utils.set_random_seed(SEED)
tf.config.threading.set_intra_op_parallelism_threads(4)
tf.config.threading.set_inter_op_parallelism_threads(2)

def features(w):
    # w: batch x 30 x 6. Match Java's block-averaged 100 Hz input, repeated to 300.
    mean = w.mean(1); var = w.var(1); sq = (w*w).mean(1)
    centered = w - mean[:, None, :]
    zcr = ((centered[:, 1:] * centered[:, :-1]) < 0).sum(1) / 299
    partner = [3,4,5,0,1,2]
    cov = (centered * centered[:, :, partner]).mean(1)
    corr = cov / np.sqrt(np.maximum(1e-12, var * var[:, partner]))
    return np.stack([mean, np.sqrt(var), np.ptp(w, axis=1), np.sqrt(sq), zcr, sq, corr], -1).reshape(-1,42).astype('float32')

def prepare():
    manifest = json.loads((DATA/'manifest.json').read_text())
    entries = {x['path']: x for x in manifest['files']}
    windows=[]; speeds=[]; motions=[]; healthx=[]; healthy=[]; groups=[]; audits=[]
    for rel, entry in entries.items():
        if not Path(rel).name.startswith('S-'): continue
        vrel = str(Path(rel).with_name('V-'+Path(rel).name[2:])).replace('\\','/')
        if vrel not in entries:
            matches=[p for p in entries if p.lower()==vrel.lower()]
            if len(matches)!=1: raise ValueError('Missing pair '+rel)
            vrel=matches[0]
        for name in (rel,vrel):
            if hashlib.sha256((DATA/name).read_bytes()).hexdigest()!=entries[name]['sha256']:
                raise ValueError('Checksum mismatch '+name)
        s=pd.read_csv(DATA/rel, encoding='latin1'); v=pd.read_csv(DATA/vrel, encoding='latin1')
        assert 'ACCELEROMETER X' in s.columns[9] and 'Indicated Vehicle Speed' in v.columns[15]
        # Parse the smartphone wall clock; the synchronized vehicle time is seconds since midnight.
        clock=s.iloc[:,8].astype(str).str.extract(r'(\d{2}):(\d{2}):(\d{2})[:_\.](\d+)').astype(float)
        st=(clock[0]*3600+clock[1]*60+clock[2]+clock[3]/1000).to_numpy()
        vt=pd.to_numeric(v.iloc[:,1],errors='coerce').to_numpy()
        # This folder is manually synchronized by its authors. Anchor the first
        # paired samples, then join elapsed times. Phone local time versus vehicle
        # UTC differs by an hour on summer drives; raw wall-clock joins are wrong.
        clock_offset=float(st[0]-vt[0])
        st=st-st[0]+vt[0]
        order=np.argsort(vt); vt=vt[order]; v=v.iloc[order]
        ix=np.searchsorted(vt,st).clip(0,len(vt)-1)
        left=(ix-1).clip(0); ix=np.where(abs(vt[left]-st)<abs(vt[ix]-st),left,ix)
        aligned=abs(vt[ix]-st)<=.061
        imu=s.iloc[:,[9,10,11,15,16,17]].apply(pd.to_numeric,errors='coerce').to_numpy(dtype='float32')
        speed=pd.to_numeric(v.iloc[ix,15],errors='coerce').to_numpy()/3.6
        accel=pd.to_numeric(v.iloc[ix,16],errors='coerce').to_numpy()*9.80665
        brake=pd.to_numeric(v.iloc[ix,25],errors='coerce').to_numpy()
        slat=pd.to_numeric(s.iloc[:,0],errors='coerce').to_numpy(); slon=pd.to_numeric(s.iloc[:,1],errors='coerce').to_numpy()
        vlat=pd.to_numeric(v.iloc[ix,2],errors='coerce').to_numpy(); vlon=pd.to_numeric(v.iloc[ix,3],errors='coerce').to_numpy()
        separation=np.hypot((slat-vlat)*111320,(slon-vlon)*111320*np.cos(np.deg2rad(vlat)))
        sats=pd.to_numeric(s.iloc[:,6].astype(str).str.extract(r'(\d+)')[0],errors='coerce').to_numpy()
        accuracy=pd.to_numeric(s.iloc[:,4],errors='coerce').to_numpy()
        valid=np.isfinite(pd.to_numeric(s.iloc[:,3],errors='coerce').to_numpy()).astype(float)
        base=np.stack([sats,np.zeros(len(s)),accuracy,valid,np.zeros(len(s))],1)
        delta=np.zeros_like(base); delta[10:]=base[10:]-base[:-10]
        hx=np.concatenate([base,delta],1)
        # Only measured fields: C/N0 and fix-age are not supplied by this dataset.
        candidates=np.arange(29,len(s),10)
        keep=[]
        for end in candidates:
            start=end-29
            if not aligned[start:end+1].all() or not np.isfinite(imu[start:end+1]).all(): continue
            if not (.075<=np.diff(st[start:end+1])).all() or not (np.diff(st[start:end+1])<=.125).all(): continue
            if not np.isfinite(speed[end]) or not 0<=speed[end]<=70: continue
            if not np.isfinite(hx[end]).all() or not np.isfinite(separation[end]): continue
            if abs(slat[end])<1 or abs(vlat[end])<1: continue
            keep.append(end)
        if not keep:
            audits.append({'recording':rel,'windows':0,'aligned_fraction':float(aligned.mean())}); continue
        end=np.array(keep)
        w=imu[end[:,None]+np.arange(-29,1)]
        # Weak supervision from CAN: idling and strong braking; no invented pothole/knock labels.
        motion=np.where(speed[end]<.5,1,np.where((accel[end]<-2)&(brake[end]>.5)&(speed[end]>2),3,0))
        health=np.where(separation[end]<=10,0,np.where(separation[end]<=35,1,2))
        family=rel.split('/')[0]
        split='test' if 'Driver A' in family else 'validation' if family.startswith('Vf ') else 'train'
        windows.append(w); speeds.append(speed[end]); motions.append(motion); healthx.append(hx[end]); healthy.append(health); groups.extend([split]*len(end))
        audits.append({'recording':rel,'split':split,'windows':len(end),'clock_offset_seconds':clock_offset,'aligned_fraction':float(aligned.mean()),'median_pair_separation_m':float(np.median(separation[end]))})
        print(rel, split,len(end),flush=True)
    arrays=dict(w=np.concatenate(windows),speed=np.concatenate(speeds).astype('float32'),motion=np.concatenate(motions),hx=np.concatenate(healthx).astype('float32'),health=np.concatenate(healthy),split=np.array(groups))
    np.savez_compressed(OUT/'windows-v2.npz',**arrays)
    (OUT/'data-audit.json').write_text(json.dumps({'repository':manifest['repository'],'revision':manifest['revision'],'files':manifest['files'],'recordings':audits},indent=2))
    return arrays

def normalized(inputs, x, mask=None):
    if mask is not None:
        inputs=tf.keras.layers.Rescaling(np.asarray(mask,dtype='float32'))(inputs)
        x=x*np.asarray(mask,dtype='float32')
    mean=x.mean(0); std=np.maximum(x.std(0),.001)
    return tf.keras.layers.Rescaling(1/std,offset=-mean/std)(inputs)

def fit(model,x,y,split,classification=False):
    a=split=='train'; b=split=='validation'
    model.compile(optimizer=tf.keras.optimizers.Adam(.001),loss='sparse_categorical_crossentropy' if classification else 'huber')
    weights=None
    if classification:
        counts=np.bincount(y[a],minlength=model.output_shape[-1]); weights={i:float(min(15,len(y[a])/max(1,len(counts)*n))) for i,n in enumerate(counts)}
    history=model.fit(x[a],y[a],validation_data=(x[b],y[b]),epochs=30,batch_size=256,class_weight=weights,verbose=2,callbacks=[tf.keras.callbacks.EarlyStopping(patience=5,restore_best_weights=True)])
    return len(history.history['loss'])

def export(name,model,x,report):
    signature=tf.function(lambda z:model(z,training=False),input_signature=[tf.TensorSpec([1,*x.shape[1:]],tf.float32)])
    converter=tf.lite.TFLiteConverter.from_concrete_functions([signature.get_concrete_function()],model)
    converter.target_spec.supported_ops=[tf.lite.OpsSet.TFLITE_BUILTINS]
    blob=converter.convert()
    path=OUT/(name+'.tflite');path.write_bytes(blob)
    interpreter=tf.lite.Interpreter(model_content=blob);interpreter.allocate_tensors()
    i=interpreter.get_input_details()[0];o=interpreter.get_output_details()[0]
    errors=[]
    for sample in x[:100]:
        sample=sample[None].astype('float32');interpreter.set_tensor(i['index'],sample);interpreter.invoke()
        errors.append(float(np.max(np.abs(interpreter.get_tensor(o['index'])-model(sample).numpy()))))
    if max(errors)>.005: raise ValueError('Export parity failed')
    report.update(input_shape=i['shape'].tolist(),output_shape=o['shape'].tolist(),sha256=hashlib.sha256(blob).hexdigest(),size_bytes=len(blob),max_tflite_error=max(errors))
    np.savez_compressed(OUT/(name+'-weights.npz'), *model.get_weights())

def main():
    # Rebuild by default so changed data never silently reuses an old cache.
    d=prepare()
    split=d['split']; a=split=='train'; t=split=='test'
    assert a.any() and t.any() and (split=='validation').any()
    report={'seed':SEED,'tensorflow':tf.__version__,'dataset':'IO-VNBD synchronized categorized recordings','split_counts':{k:int((split==k).sum()) for k in np.unique(split)},'test_driver':'Driver A (all S recordings)','validation_family':'Vfa (Driver E)','models':{}}
    x=features(d['w']); inp=tf.keras.Input((42,),batch_size=1); z=normalized(inp,x[a]);z=tf.keras.layers.Dense(48,activation='relu')(z);z=tf.keras.layers.Dense(24,activation='relu')(z)
    # Three supported categories mapped into the required five-class tensor.
    p=tf.keras.layers.Dense(3,activation='softmax')(z)
    mapping=np.zeros((3,5),dtype='float32');mapping[0,0]=mapping[1,1]=mapping[2,3]=1
    out=tf.keras.layers.Dense(5,use_bias=False,trainable=False,kernel_initializer=tf.keras.initializers.Constant(mapping))(p)
    model=tf.keras.Model(inp,out); epochs=fit(model,x,d['motion'],split,True)
    pred=model.predict(x[t],batch_size=256,verbose=0).argmax(1)
    r={'epochs':epochs,'supervision':'CAN speed <0.5 m/s = idling; CAN acceleration < -2 m/s2 with brake applied = hard_brake; otherwise driving_normal','supported_classes':[0,1,3],'unsupported_classes':[2,4],'balanced_accuracy':float(balanced_accuracy_score(d['motion'][t],pred)),'confusion_matrix':confusion_matrix(d['motion'][t],pred,labels=[0,1,3]).tolist(),'test_class_counts':np.bincount(d['motion'][t],minlength=5).tolist()};export('motion',model,x[t],r);report['models']['motion']=r
    (OUT/'training-report.json').write_text(json.dumps(report,indent=2))
    x=d['hx'];inp=tf.keras.Input((10,),batch_size=1);z=normalized(inp,x[a],[1,0,1,1,0,1,0,1,1,0]);z=tf.keras.layers.Dense(24,activation='relu')(z);z=tf.keras.layers.Dense(12,activation='relu')(z);out=tf.keras.layers.Dense(3,activation='softmax')(z)
    model=tf.keras.Model(inp,out);epochs=fit(model,x,d['health'],split,True);pred=model.predict(x[t],batch_size=256,verbose=0).argmax(1)
    r={'epochs':epochs,'supervision':'Smartphone versus vehicle GNSS separation <=10m / <=35m / >35m. Proxy quality labels, not surveyed ground truth or actual blackout events.','masked_features':[1,4,6,9],'balanced_accuracy':float(balanced_accuracy_score(d['health'][t],pred)),'confusion_matrix':confusion_matrix(d['health'][t],pred,labels=[0,1,2]).tolist(),'test_class_counts':np.bincount(d['health'][t],minlength=3).tolist()};export('gps-health',model,x[t],r);report['models']['gps-health']=r
    (OUT/'training-report.json').write_text(json.dumps(report,indent=2))
    # 10 Hz source repeated to the fixed 100 Hz API. Pooling removes artificial repetition.
    x=np.repeat(d['w'],10,axis=1);inp=tf.keras.Input((300,6),batch_size=1);z=tf.keras.layers.AveragePooling1D(10)(inp)
    z=tf.keras.layers.Lambda(lambda q:tf.stack([tf.sqrt(tf.reduce_sum(q[:,:,:3]**2,axis=-1)+1e-6)-9.80665,tf.sqrt(tf.reduce_sum(q[:,:,3:]**2,axis=-1)+1e-6)],axis=-1))(z)
    z=tf.keras.layers.Conv1D(24,5,padding='same',activation='relu')(z);z=tf.keras.layers.Conv1D(24,5,padding='same',activation='relu')(z);z=tf.keras.layers.GlobalAveragePooling1D()(z);z=tf.keras.layers.Dense(32,activation='relu')(z);out=tf.keras.layers.Dense(1,activation='softplus')(z)
    model=tf.keras.Model(inp,out);epochs=fit(model,x,d['speed'],split);pred=model.predict(x[t],batch_size=256,verbose=0).ravel();baseline=np.full_like(pred,np.median(d['speed'][a]))
    r={'epochs':epochs,'target':'Time-aligned indicated CAN vehicle speed in m/s','test_mae_mps':float(mean_absolute_error(d['speed'][t],pred)),'test_rmse_mps':float(np.sqrt(mean_squared_error(d['speed'][t],pred))),'median_baseline_mae_mps':float(mean_absolute_error(d['speed'][t],baseline))};export('speed',model,x[t],r);report['models']['speed']=r
    report['limitations']=['10 Hz source cannot provide 100 Hz signal detail. Native inputs are block averaged to match.','Motion uses weak CAN labels; pothole and phone_knocked outputs are fixed at zero.','GNSS labels measure agreement between receivers, not absolute accuracy. C/N0 and fix-age inputs are masked.','Held-out UK driving data does not validate Bhopal devices or real tunnel drift.','Trained research models run inference; fusion-changing use remains disabled until physical-device validation.']
    (OUT/'training-report.json').write_text(json.dumps(report,indent=2))
    assets=ROOT/'android/app/src/main/assets/models'
    manifest={'version':'1.0.0','dataset':'IO-VNBD','revision':json.loads((DATA/'manifest.json').read_text())['revision'],'models':[]}
    for name,r in report['models'].items():
        (assets/(name+'.tflite')).write_bytes((OUT/(name+'.tflite')).read_bytes())
        manifest['models'].append({'name':name,'version':'1.0.0','placeholder':False,'validated':False,'sha256':r['sha256'],'dataset':'IO-VNBD'})
    (assets/'manifest.json').write_text(json.dumps(manifest,indent=2))
    (ROOT/'src/model-manifest.json').write_text(json.dumps(manifest,indent=2))
    print(json.dumps(report,indent=2),flush=True)

if __name__=='__main__': main()
