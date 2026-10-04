"""Train tiny synthetic Keras networks and export exact production tensor shapes.
PLACEHOLDER MODEL — replace via POST /api/models once real models are trained.
Run in Python 3.11/3.12: pip install tensorflow numpy; python ml/generate_placeholders.py
"""
import pathlib
import numpy as np
import tensorflow as tf
ROOT=pathlib.Path(__file__).resolve().parents[1]/'android/app/src/main/assets/models'
ROOT.mkdir(parents=True,exist_ok=True)
tf.keras.utils.set_random_seed(42)
rng=np.random.default_rng(42)
for name,shape,classes in [('motion',(42,),5),('gps-health',(10,),3),('speed',(300,6),1)]:
    x=rng.normal(size=(128,*shape)).astype('float32')
    y=(rng.integers(0,classes,128) if classes>1 else np.abs(rng.normal(10,4,(128,1)))).astype('float32')
    model=tf.keras.Sequential([tf.keras.Input(shape=shape),tf.keras.layers.Flatten(),tf.keras.layers.Dense(16,activation='relu'),tf.keras.layers.Dense(classes,activation='softmax' if classes>1 else 'softplus')])
    model.compile(optimizer='adam',loss='sparse_categorical_crossentropy' if classes>1 else 'mse')
    model.fit(x,y,epochs=2,batch_size=32,verbose=0)
    converted=tf.lite.TFLiteConverter.from_keras_model(model).convert()
    (ROOT/(name+'.tflite')).write_bytes(converted)
    print(name,len(converted),'PLACEHOLDER — no physical accuracy')
