"""Regression checks plus held-out stationary-speed diagnostics on exported models."""
from pathlib import Path
import json,hashlib
import numpy as np
import tensorflow as tf
ROOT=Path(__file__).resolve().parents[1]; OUT=ROOT/'artifacts/ml'
data=dict(np.load(OUT/'windows-v2.npz'));test=np.flatnonzero(data['split']=='test')
manifest=json.loads((ROOT/'android/app/src/main/assets/models/manifest.json').read_text())
summary={}
for m in manifest['models']:
    path=ROOT/'android/app/src/main/assets/models'/(m['name']+'.tflite')
    assert hashlib.sha256(path.read_bytes()).hexdigest()==m['sha256']
    print('Checking',m['name'],flush=True)
    interpreter=tf.lite.Interpreter(model_path=str(path),num_threads=2);interpreter.allocate_tensors()
    inp=interpreter.get_input_details()[0];out=interpreter.get_output_details()[0]
    assert inp['shape'][0]==out['shape'][0]==1
    def run(x):
        interpreter.set_tensor(inp['index'],np.asarray(x,dtype='float32'));interpreter.invoke()
        p=interpreter.get_tensor(out['index']);assert np.isfinite(p).all();return p
    if m['name']=='motion':
        from train_iovnbd import features
        x=features(data['w'][test[::100]])
        p=np.concatenate([run(row[None]) for row in x]);assert np.all(p[:,[2,4]]==0)
        assert np.allclose(p.sum(1),1,atol=1e-5)
        summary[m['name']]={'windows_checked':len(x),'unsupported_classes_zero':True}
    elif m['name']=='gps-health':
        x=data['hx'][test[::100]];p=[]
        for row in x:
            a=run(row[None]);changed=row.copy();changed[[1,4,6,9]]=10000
            assert np.allclose(a,run(changed[None]),atol=1e-6)
            assert np.allclose(a.sum(),1,atol=1e-5)
        summary[m['name']]={'windows_checked':len(x),'missing_features_masked':True}
    else:
        summary[m['name']]={}
        for label,ids in [('stationary',test[data['speed'][test]<.5]),('moving',test[data['speed'][test]>=.5])]:
            ids=ids[::max(1,len(ids)//1000)]
            pred=np.array([run(np.repeat(data['w'][i:i+1],10,axis=1))[0,0] for i in ids])
            assert (pred>=0).all()
            summary[m['name']][label]={'windows_checked':len(ids),'mae_mps':float(np.abs(pred-data['speed'][ids]).mean()),'median_prediction_mps':float(np.median(pred)),'p95_prediction_mps':float(np.percentile(pred,95))}
    assert m['placeholder'] is False and m['validated'] is False
summary['fusion_gate']='Bundled learned corrections remain disabled pending phone validation. Live navigation uses the ESKF after mount alignment, with GNSS fallback beforehand. These model tests do not validate navigation accuracy.'
(OUT/'export-regression.json').write_text(json.dumps(summary,indent=2))
print(json.dumps(summary,indent=2))
