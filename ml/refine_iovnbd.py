"""Refinement experiment; preserves shipped models and selects checkpoints using validation only.

Driver A is a previously examined regression set, not a new blind test set.
Missing pothole/knock/CN0 labels are never manufactured.
"""
import json
import hashlib
from pathlib import Path
import numpy as np
import tensorflow as tf
from sklearn.metrics import mean_absolute_error, balanced_accuracy_score, confusion_matrix
import train_iovnbd as base

ROOT=base.ROOT
OUT=ROOT/'artifacts/ml/refinement'
OUT.mkdir(parents=True,exist_ok=True)
base.OUT=OUT
d=dict(np.load(ROOT/'artifacts/ml/windows-v2.npz'))
train=d['split']=='train'; val=d['split']=='validation'; test=d['split']=='test'
report={'dataset':'IO-VNBD','seed':42,'cache_sha256':hashlib.sha256((ROOT/'artifacts/ml/windows-v2.npz').read_bytes()).hexdigest(),
        'selection':'Validation split only; Driver A is a reused regression set, not a fresh blind evaluation.',
        'models':{},'promotion':'No automatic promotion; candidate quality and physical-device validation required.'}

def fit(model,x,y,classification=False):
    weights=None
    if classification:
        counts=np.bincount(y[train]); weights=np.array([min(12,len(y[train])/(len(counts)*max(1,counts[int(c)]))) for c in y[train]])
    else: weights=np.where(y[train]<.5,3.,1.)
    model.compile(optimizer=tf.keras.optimizers.Adam(.0005),loss='sparse_categorical_crossentropy' if classification else 'huber')
    model.fit(x[train],y[train],sample_weight=weights,validation_data=(x[val],y[val]),epochs=25,batch_size=256,verbose=2,
        callbacks=[tf.keras.callbacks.EarlyStopping(patience=5,restore_best_weights=True),tf.keras.callbacks.ReduceLROnPlateau(patience=2,factor=.5)])

def evaluate(name,model,x,y,classification=False):
    r={}
    for label,mask in [('validation',val),('regression_driver_a',test)]:
        p=model.predict(x[mask],batch_size=256,verbose=0)
        if classification:
            p=p.argmax(1);r[label]={'balanced_accuracy':float(balanced_accuracy_score(y[mask],p)),
                'confusion_matrix':confusion_matrix(y[mask],p).tolist()}
        else:
            p=p.ravel();stop=y[mask]<.5
            r[label]={'mae_mps':float(mean_absolute_error(y[mask],p)),
                'stationary_mae_mps':float(mean_absolute_error(y[mask][stop],p[stop])),
                'stationary_p95_mps':float(np.percentile(p[stop],95)),
                'moving_mae_mps':float(mean_absolute_error(y[mask][~stop],p[~stop]))}
    base.export(name,model,x[test],r)
    report['models'][name]=r
    (OUT/'report.json').write_text(json.dumps(report,indent=2))

# Richer rotation-invariant temporal features; no GNSS or CAN inputs in the speed estimator.
x=np.repeat(d['w'],10,axis=1)
inp=tf.keras.Input((300,6),batch_size=1)
z=tf.keras.layers.AveragePooling1D(10)(inp)
def invariant(q):
    a=q[:,:,:3];g=q[:,:,3:]
    ac=a-tf.reduce_mean(a,axis=1,keepdims=True)
    return tf.stack([tf.sqrt(tf.reduce_sum(a*a,-1)+1e-6)-9.80665,
        tf.sqrt(tf.reduce_sum(g*g,-1)+1e-6),tf.sqrt(tf.reduce_sum(ac*ac,-1)+1e-6),
        tf.reduce_sum(a*g,-1)/9.80665],axis=-1)
z=tf.keras.layers.Lambda(invariant)(z)
stats=invariant(tf.constant(d['w'][train])).numpy()
mean=stats.mean((0,1));std=np.maximum(stats.std((0,1)),.001)
z=tf.keras.layers.Rescaling(1/std,offset=-mean/std)(z)
for dilation in (1,2,4): z=tf.keras.layers.Conv1D(24,3,padding='same',dilation_rate=dilation,activation='relu')(z)
z=tf.keras.layers.Concatenate()([tf.keras.layers.GlobalAveragePooling1D()(z),tf.keras.layers.GlobalMaxPooling1D()(z)])
z=tf.keras.layers.Dense(32,activation='relu',kernel_regularizer=tf.keras.regularizers.l2(.0001))(z)
out=tf.keras.layers.Dense(1,activation='softplus')(z)
model=tf.keras.Model(inp,out);fit(model,x,d['speed']);evaluate('speed',model,x,d['speed'])
del x,stats

x=base.features(d['w']);inp=tf.keras.Input((42,),batch_size=1);z=base.normalized(inp,x[train])
for units in (64,32):
    z=tf.keras.layers.Dense(units,activation='relu',kernel_regularizer=tf.keras.regularizers.l2(.001))(z)
    z=tf.keras.layers.Dropout(.2)(z)
p=tf.keras.layers.Dense(3,activation='softmax')(z)
mapping=np.zeros((3,5),dtype='float32');mapping[0,0]=mapping[1,1]=mapping[2,3]=1
out=tf.keras.layers.Dense(5,use_bias=False,trainable=False,kernel_initializer=tf.keras.initializers.Constant(mapping))(p)
model=tf.keras.Model(inp,out);fit(model,x,d['motion'],True);evaluate('motion',model,x,d['motion'],True)

# Smaller regularized model for receiver-agreement proxy; cannot learn actual blackout without labels.
x=d['hx'];inp=tf.keras.Input((10,),batch_size=1);z=base.normalized(inp,x[train],[1,0,1,1,0,1,0,1,1,0])
z=tf.keras.layers.Dense(12,activation='relu',kernel_regularizer=tf.keras.regularizers.l2(.01))(z)
out=tf.keras.layers.Dense(3,activation='softmax')(z)
model=tf.keras.Model(inp,out);fit(model,x,d['health'],True);evaluate('gps-health',model,x,d['health'],True)
report['limitations']=['Source is 10 Hz; cannot recover missing high-rate detail.',
    'Motion pothole/phone_knocked outputs remain zero: no annotations.',
    'GPS-health uses receiver-agreement proxy, not actual blackout labels; C/N0 and age remain masked.',
    'Real phone recordings with reference trajectory and annotated events are still required.']
(OUT/'report.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2),flush=True)
