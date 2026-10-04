"""Real converter and Java-process tests. Synthetic inputs prove plumbing only."""
import csv
import json
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest
import numpy as np
from laptop_agent.common import ROOT, sha
from laptop_agent.evaluate import predictions
from laptop_agent.replay import compile_replay


class IntegrationTests(unittest.TestCase):
    def test_existing_trainer_exports_separate_executable_candidate(self):
        from phone_learning import train
        base=ROOT/'android/app/src/main/assets/models/speed.tflite'; before=sha(base)
        y=np.tile(np.r_[np.zeros(40),np.linspace(1,15,80)],5).astype(np.float32)
        windows=np.zeros((600,300,6),np.float32)
        windows[:,:,0]=y[:,None];windows[:,:,2]=9.80665
        data={'w':windows,'speed':y,'session':np.repeat(['a','b','c','d','e'],120),
              'split':np.repeat(['train','train','train','validation','test'],120)}
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);report=train(data,base,out)
            self.assertEqual(report['status'],'candidate_only')
            result=predictions(out/'speed-candidate.tflite',windows[-120:])
            self.assertTrue(np.isfinite(result).all())
            self.assertLess(float(np.mean(abs(result-y[-120:]))),.1)
        self.assertEqual(sha(base),before)

    def test_real_java_replay_emits_outage_observations(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);command=compile_replay(out)
            source=out/'input.csv';target=out/'output.csv'
            with source.open('w',newline='') as f:
                writer=csv.writer(f);writer.writerow(['test-fixture'])
                for i in range(10000):
                    t=1_000_000_000+i*10_000_000;seconds=i*.01
                    hidden=60<=seconds<90;reference=i%100==0
                    writer.writerow([t,0,0,9.80665,0,0,0,0,0,9.80665,0,1,t,
                                     23+10*seconds/111320,77,0,3,10,.2,0,5,int(reference),int(hidden),10,10,0])
            subprocess.run(command+[str(source),str(target)],check=True,capture_output=True,timeout=120)
            with target.open() as f:rows=list(csv.DictReader(f))
            hidden=[r for r in rows if r['hidden']=='1']
            self.assertEqual(len(hidden),30)
            self.assertTrue(all(np.isfinite(float(r['candidate_error'])) for r in hidden))
            self.assertTrue(all(r['baseline_available']=='1' for r in hidden))


if __name__=='__main__':unittest.main()
