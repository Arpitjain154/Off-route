"""Synthetic contract checks only; these fixtures do not establish physical accuracy."""
import json
from pathlib import Path
import struct
import tempfile
import unittest
import uuid
import numpy as np

class PhoneLearningTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
    def module(self):
        self.assertTrue(Path(__file__).with_name('phone_learning.py').exists(),'Phone ingestion missing')
        import phone_learning
        return phone_learning
    def recording(self,source='device',quality=True,seconds=8):
        session=str(uuid.uuid4());path=self.root/(session+'.drlog')
        header=b'DRLOG'+struct.pack('<BH',1,100)
        header+=b''.join(s.encode().ljust(64,b'\0') for s in ('Test phone','dashboard',session))
        start=12_345_678_901_230_000
        rows=[];quality_rows=['t_ns,fix_t_ns,speed_accuracy_mps,gyro_fresh']
        for i in range(seconds*100):
            t=start+i*10_000_000;fix=start+(i//100)*1_000_000_000
            rows.append(struct.pack('<q14fBfB',t,i/100,0,9.80665,0,0,.1,0,0,0,23,77,500,3+i//100,4,10,35,3))
            quality_rows.append(f'{t},{fix},0.3,1')
        path.write_bytes(header+b''.join(rows))
        if quality:
            path.with_suffix('.quality.csv').write_text('\n'.join(quality_rows)+'\n')
            path.with_suffix('.training.json').write_text(json.dumps(dict(schemaVersion=1,source=source,sessionId=session)))
        return path
    def test_unique_fresh_fixes_and_tensor_contract(self):
        m=self.module();w,y,t,a=m.read_session(self.recording())
        self.assertEqual(w.shape,(5,300,6));np.testing.assert_array_equal(y,[6,7,8,9,10])
        self.assertEqual(t[0],12_345_681_901_230_000);self.assertEqual(len(set(t)),5)
        self.assertAlmostEqual(float(w[0,0,0]),.055,places=5)
    def test_delayed_fix_window_ends_at_measurement_not_arrival(self):
        m=self.module();path=self.recording();p=path.with_suffix('.quality.csv');rows=p.read_text().splitlines()
        start=12_345_678_901_230_000;out=[rows[0]]
        for i,row in enumerate(rows[1:]):
            cols=row.split(',');cols[1]=str(start+((i-50)//100)*1_000_000_000) if i>=50 else '0';out.append(','.join(cols))
        p.write_text('\n'.join(out));w,y,t,a=m.read_session(path)
        # First label has fix timestamp start+3s, delivered at start+3.5s.
        self.assertEqual(t[0],start+3_500_000_000)
        self.assertAlmostEqual(float(w[0,-1,0]),2.955,places=5)

    def test_no_legacy_or_simulation_labels(self):
        m=self.module()
        for path,reason in [(self.recording(quality=False),'missing_quality_metadata'),(self.recording(source='simulation'),'not_physical_device')]:
            w,y,t,a=m.read_session(path);self.assertEqual(len(w),0);self.assertEqual(a['rejected_session'],reason)
    def test_quality_gates(self):
        m=self.module()
        for index,value in [(2,'3'),(3,'0'),(1,'1')]:
            path=self.recording();p=path.with_suffix('.quality.csv');rows=p.read_text().splitlines();changed=[rows[0]]
            for row in rows[1:]:
                cols=row.split(',');cols[index]=value;changed.append(','.join(cols))
            p.write_text('\n'.join(changed));w,y,t,a=m.read_session(path);self.assertEqual(len(w),0)
    def test_candidate_trial_is_not_ordinary_training_data(self):
        m=self.module();path=self.recording();metadata=path.with_suffix('.training.json')
        value=json.loads(metadata.read_text());value['candidateTrialSha256']='a'*64
        metadata.write_text(json.dumps(value))
        w,y,t,a=m.read_session(path)
        self.assertEqual(len(w),0)
        self.assertEqual(a['rejected_session'],'candidate_trial_excluded')
    def test_sensor_gap_is_not_interpolated_away(self):
        m=self.module();path=self.recording();blob=path.read_bytes()
        path.write_bytes(blob[:200+350*70]+blob[200+400*70:])
        w,y,t,a=m.read_session(path);np.testing.assert_array_equal(y,[6,10]);self.assertGreater(a['rejections']['imu_gap'],0)
    def test_missing_quality_rejects_window(self):
        m=self.module();path=self.recording();p=path.with_suffix('.quality.csv');rows=p.read_text().splitlines()
        del rows[251];p.write_text('\n'.join(rows));w,y,t,a=m.read_session(path)
        np.testing.assert_array_equal(y,[9,10])
    def test_duplicate_session_cannot_cross_splits(self):
        m=self.module();path=self.recording();d,r=m.build_dataset([path,path],min_session_fixes=1)
        self.assertEqual(len(d['w']),5);self.assertEqual(len(set(d['split'])),1)
        self.assertEqual(r['sessions'][1]['rejected_session'],'duplicate_session')
    def test_training_blocks_insufficient_independent_data(self):
        m=self.module();d={'w':np.zeros((120,300,6)),'speed':np.ones(120),'session':np.full(120,'one'),'split':np.full(120,'train')}
        reasons=m.training_blockers(d)
        self.assertTrue(any('sessions' in s for s in reasons));self.assertTrue(any('validation' in s for s in reasons));self.assertTrue(any('test' in s for s in reasons))
    def test_ridge_fits_real_coefficients_and_regression_gate(self):
        m=self.module();x=np.arange(30,dtype=float).reshape(-1,1)
        model=m.fit_ridge(x,2*x[:,0]+1,.001)
        np.testing.assert_allclose(m.predict_ridge(model,np.array([[30.],[31.]])),[61,63],atol=.01)
        self.assertTrue(m.promotion_blockers(np.array([0.,3.,6.]),np.array([2.,6.,8.]),np.array([.1,3.2,6.1])))
if __name__=='__main__':unittest.main()
