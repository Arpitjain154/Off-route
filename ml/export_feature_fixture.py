"""Export an actual IO-VNBD window and Python features for Java parity testing."""
from train_iovnbd import ROOT, OUT, features
import numpy as np
d=np.load(OUT/'windows-v2.npz')
w=d['w'][np.flatnonzero(d['split']=='test')[0]:][:1]
path=ROOT/'android/app/src/test/resources/iovnbd-feature-fixture.txt'
path.parent.mkdir(parents=True,exist_ok=True)
with path.open('w') as f:
    f.write(','.join(map(str,features(w)[0]))+'\n')
    np.savetxt(f,np.repeat(w[0],10,axis=0),delimiter=',')
print(path)
