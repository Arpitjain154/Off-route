"""Fixture causality tests; synthetic traces are not accuracy evidence."""
import unittest
import numpy as np
from evaluate_outages import mapping


class OutageFixtureTest(unittest.TestCase):
    def fixture(self):
        t=np.arange(0,120,.1);a=np.zeros((len(t),15));a[:,0]=t
        a[:,3]=9.80665;a[:,9]=3;a[:,10]=10
        rate=.05*np.sin(t/8);a[:,5]=-rate
        a[:,11]=np.cumsum(rate*.1)*180/np.pi
        return a,np.ones(len(t),dtype=bool)

    def test_learns_vertical_axis_before_loss(self):
        a,changed=self.fixture();_,audit=mapping(a,changed,60)
        self.assertTrue(audit['gyro_reliable']);self.assertEqual(audit['gyro_axis'],1)
        self.assertLess(audit['last_calibration_s'],60)

    def test_hidden_reference_and_gnss_cannot_change_mapping(self):
        a,changed=self.fixture();_,before=mapping(a,changed,60)
        a[a[:,0]>=60,7:15]=999;a[:,12:15]=-999
        _,after=mapping(a,changed,60);self.assertEqual(before,after)

    def test_sparse_gnss_is_used_without_future_samples(self):
        a,_=self.fixture();changed=np.zeros(len(a),dtype=bool);changed[::90]=True
        _,audit=mapping(a,changed,60)
        self.assertGreaterEqual(audit['calibration_samples'],4)
        self.assertTrue(audit['gyro_reliable'])


if __name__=='__main__':unittest.main()
