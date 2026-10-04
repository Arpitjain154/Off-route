import unittest
import numpy as np
from vehicle_speed_candidate import features
class VehicleSpeedFeatureTest(unittest.TestCase):
 def test_phone_rotation_does_not_change_features(self):
  rng=np.random.default_rng(17);w=rng.normal(size=(4,300,6)).astype(np.float32);w[:,:,2]+=9.8
  q,_=np.linalg.qr(rng.normal(size=(3,3)));rotated=np.concatenate((w[:,:,:3]@q,w[:,:,3:]@q),axis=2)
  np.testing.assert_allclose(features(w),features(rotated),rtol=2e-5,atol=1e-5)
 def test_zero_motion_is_finite_and_batch_independent(self):
  w=np.zeros((2,300,6),np.float32);w[:,:,2]=9.80665
  x=features(w);self.assertEqual(x.shape,(2,20));self.assertTrue(np.isfinite(x).all());np.testing.assert_allclose(features(w[:1]),x[:1])
if __name__=='__main__':unittest.main()
