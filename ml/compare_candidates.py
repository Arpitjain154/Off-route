"""Compare shipped and candidate TFLite models on identical validation examples."""
import os
os.environ['TF_CPP_MIN_LOG_LEVEL']='2'
from pathlib import Path
import json
import numpy as np
import tensorflow as tf
from sklearn.metrics import balanced_accuracy_score,mean_absolute_error
from train_iovnbd import features
ROOT=Path(__file__).resolve().parents[1]; OUT=ROOT/'artifacts/ml/refinement'
d=dict(np.load(ROOT/'artifacts/ml/windows-v2.npz'));mask=d['split']=='validation'
results={}
for name in ['speed','motion','gps-health']:
    x=np.repeat(d['w'][mask],10,1) if name=='speed' else features(d['w'][mask]) if name=='motion' else d['hx'][mask]
    y=d['speed' if name=='speed' else 'motion' if name=='motion' else 'health'][mask]
    results[name]={}
    for label,path in [('shipped',ROOT/'artifacts/ml'/(name+'.tflite')),('candidate',OUT/(name+'.tflite'))]:
        model=tf.lite.Interpreter(model_path=str(path),num_threads=2);model.allocate_tensors()
        inp=model.get_input_details()[0]['index'];out=model.get_output_details()[0]['index'];pred=[]
        for row in x:
            model.set_tensor(inp,row[None].astype('float32'));model.invoke();pred.append(model.get_tensor(out)[0])
        p=np.array(pred)
        results[name][label]={'mae_mps':float(mean_absolute_error(y,p.ravel()))} if name=='speed' else {'balanced_accuracy':float(balanced_accuracy_score(y,p.argmax(1)))}
    print(name,results[name],flush=True)
(OUT/'validation-comparison.json').write_text(json.dumps(results,indent=2))
