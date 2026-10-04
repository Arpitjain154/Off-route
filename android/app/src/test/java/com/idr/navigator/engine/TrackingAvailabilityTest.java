package com.idr.navigator.engine;

import static org.junit.Assert.*;
import org.junit.Test;

public class TrackingAvailabilityTest {
    private static final long S=1_000_000_000L;
    @Test public void losingMountAlignmentHoldsLastEstimateInsteadOfJumpingToOldGps() {
        LiveNavigation n=new LiveNavigation();
        n.gravity(S,0,0,9.80665);n.compass(S,0,5);
        n.fix(new NavigationFusion.Fix(S,23.3,77.35,0,3,10,.2,0,5),S);
        n.imu(new ImuSample(S,0,0,9.80665,0,0,0));n.providerDisabled();
        for(int i=1;i<=500;i++) {
            long t=S+i*10_000_000L;n.gravity(t,0,0,9.80665);n.imu(new ImuSample(t,0,0,9.80665,0,0,0));
        }
        assertTrue(n.estimated(6*S));
        double lat=n.latitude(),lon=n.longitude(),uncertainty=n.uncertainty(6*S);
        assertTrue((lat-23.3)*111320>45);
        long moved=6*S+10_000_000L;
        n.gravity(moved,0,4.903325,8.492808);n.tick(moved);
        assertFalse(n.trackingAvailable(moved));assertFalse(n.speedAvailable(moved));
        assertEquals("Do not jump backwards to the stale GPS fix",lat,n.latitude(),1e-9);
        assertEquals(lon,n.longitude(),1e-9);
        assertTrue("Do not reset uncertainty to stale GPS accuracy",n.uncertainty(moved)>=uncertainty);
        assertTrue(n.status(moved).contains("Phone moved"));
        n.fix(new NavigationFusion.Fix(7*S,23.301,77.35,0,3,9,.2,0,5),7*S);
        assertTrue(n.fresh(7*S));assertEquals(23.301,n.latitude(),1e-9);
    }
}
