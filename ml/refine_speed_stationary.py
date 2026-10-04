"""Candidate only: train speed with an explicit stationary gate on recording-disjoint IO-VNBD.
Selection uses the existing validation family; Driver A remains a reused regression set.
No model is promoted automatically by this experiment.
"""
import os
os.environ['TF_CPP_MIN_LOG_LEVEL']='2';os.environ['TF_ENABLE_ONEDNN_OPTS']='0'
import json,hashlib
from pathlib import Path
import numpy as np
import tensorflow as tf
import train_iovnbd as base
tf.keras.utils.set_random_seed(2026)
OUT=base.ROOT/'artifacts/qa-1.5/speed-candidate';OUT.mkdir(exist_ok=True)
base.OUT=OUT;d=dict(np.load(base.ROOT/'artifacts/ml/windows-v2.npz'));train=d['split']=='train';valid=d['split']=='validation'
x=d['w'];y=d['speed'];batch=256
def batches(mask,shuffle=False):
    ds=tf.data.Dataset.from_tensor_slices((x[mask],y[mask]))
    if shuffle:ds=ds.shuffle(20000,seed=2026)
    return ds.batch(batch).map(lambda a,b:(tf.repeat(a,10,axis=1),b)).prefetch(1)
inp=tf.keras.Input((300,6),batch_size=1);q=tf.keras.layers.AveragePooling1D(10)(inp)
def invariant(q):
    a=q[:,:,:3];g=q[:,:,3:];ac=a-tf.reduce_mean(a,1,keepdims=True)
    return tf.stack([tf.sqrt(tf.reduce_sum(a*a,-1)+1e-6)-9.80665,tf.sqrt(tf.reduce_sum(g*g,-1)+1e-6),
                     tf.sqrt(tf.reduce_sum(ac*ac,-1)+1e-6),tf.reduce_sum(a*g,-1)/9.80665],-1)
stats=invariant(tf.constant(x[train])).numpy();mean=stats.mean((0,1));std=np.maximum(.001,stats.std((0,1)));del stats
z=tf.keras.layers.Lambda(invariant)(q);z=tf.keras.layers.Rescaling(1/std,-mean/std)(z)
for dilation in (1,2,4):z=tf.keras.layers.Conv1D(32,3,padding='same',dilation_rate=dilation,activation='relu')(z)
z=tf.keras.layers.Concatenate()([tf.keras.layers.GlobalAveragePooling1D()(z),tf.keras.layers.GlobalMaxPooling1D()(z)])
z=tf.keras.layers.Dense(48,activation='relu',kernel_regularizer=tf.keras.regularizers.l2(.001))(z)
z=tf.keras.layers.Dropout(.15)(z)
moving=tf.keras.layers.Dense(1,activation='sigmoid',name='moving')(z)
magnitude=tf.keras.layers.Dense(1,activation='softplus',name='magnitude')(z)
out=tf.keras.layers.Multiply()([moving,magnitude]);model=tf.keras.Model(inp,out)
def stop_weighted_huber(true,pred):
    true=tf.reshape(true,(-1,1));err=pred-true;absolute=tf.abs(err)
    return tf.reduce_mean(tf.where(absolute<1,.5*err*err,absolute-.5)*tf.where(true<.5,5.,1.))
model.compile(optimizer=tf.keras.optimizers.Adam(.0005),loss=stop_weighted_huber)
history=model.fit(batches(train,True),validation_data=batches(valid),epochs=20,verbose=2,
    callbacks=[tf.keras.callbacks.EarlyStopping(patience=4,restore_best_weights=True),tf.keras.callbacks.ReduceLROnPlateau(patience=2,factor=.5)])
report={'seed':2026,'selection':'Existing validation family only; no automatic promotion','models':{},'epochs':len(history.history['loss'])}
for label,mask in [('validation',valid),('regression_driver_a',d['split']=='test')]:
    p=model.predict(batches(mask).map(lambda a,b:a),verbose=0).ravel();actual=y[mask];stop=actual<.5
    report[label]={'mae_mps':float(np.abs(p-actual).mean()),'stationary_mae_mps':float(np.abs(p[stop]-actual[stop]).mean()),
                   'stationary_p95_mps':float(np.percentile(p[stop],95)),'moving_mae_mps':float(np.abs(p[~stop]-actual[~stop]).mean())}
sample=np.repeat(x[np.flatnonzero(valid)[:32]],10,axis=1)
base.export('speed',model,sample,report)
report['limitations']=['Absolute IMU speed is environment-dependent; no guarantee of unseen-phone accuracy.',
    'No annotated walking, pothole, phone-knock or tunnel events in this training target.',
    'A candidate must improve validation without sacrificing moving-speed behavior before promotion.']
(OUT/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2),flush=True)
