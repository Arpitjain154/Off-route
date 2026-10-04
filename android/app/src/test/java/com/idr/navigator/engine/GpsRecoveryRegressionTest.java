package com.idr.navigator.engine;

import static org.junit.Assert.*;
import org.junit.Test;

/** Recovery timing regressions; these do not claim GPS-off positioning accuracy. */
public class GpsRecoveryRegressionTest {
    private static final long S=1_000_000_000L, RETURN=11*S;
    private NavigationFusion.Fix fix(long t,double lat,double accuracy) {
        return new NavigationFusion.Fix(t,lat,77.35,0,accuracy,5,.25,0,5);
    }
    private LiveNavigation outage() {
        var n=new LiveNavigation();
        n.gravity(S,0,0,9.80665);n.compass(S,0,5);
        assertTrue(n.fix(fix(S,23.3,3),S));
        n.imu(new ImuSample(S,0,0,9.80665,0,0,0));n.providerDisabled();
        for(int i=1;i<=1000;i++) {
            long t=S+i*10_000_000L;
            n.gravity(t,0,0,9.80665);n.imu(new ImuSample(t,0,0,9.80665,0,0,0));
        }
        assertTrue(n.estimated(RETURN));return n;
    }
    @Test public void largeOffsetBlendsWithoutJumpAndClearsWithinTwoSeconds() {
        for(double offset:new double[]{-150,150}) {
            var n=outage();double prior=n.latitude(),target=prior+offset/111320;
            assertTrue(n.fix(fix(RETURN,target,4),RETURN));
            assertEquals("First recovery frame remains continuous",prior,n.latitude(),1e-10);
            assertTrue("Reported uncertainty includes the unresolved offset",n.uncertainty(RETURN)>149);
            n.tick(RETURN+S);
            assertEquals(prior+offset/2/111320,n.latitude(),1e-10);
            n.tick(RETURN+2*S);
            assertEquals("Fresh GPS must not retain a 150 m offset for eight seconds",target,n.latitude(),1e-10);
            assertEquals(4,n.uncertainty(RETURN+2*S),1e-8);
            assertEquals("Live GNSS tracking",n.status(RETURN+2*S));
        }
    }
    @Test public void newGoodFixesDoNotRestartTheRecoveryClock() {
        var n=outage();double target=n.latitude()+150.0/111320;
        n.fix(fix(RETURN,target,4),RETURN);
        n.fix(fix(RETURN+S,target+5.0/111320,4),RETURN+S);
        n.fix(fix(RETURN+2*S,target+10.0/111320,4),RETURN+2*S);
        assertEquals(target+10.0/111320,n.latitude(),1e-10);
        assertEquals(5,n.speed(RETURN+2*S),1e-10);
    }
    @Test public void poorOrStaleReturnCannotStartGpsRecovery() {
        var n=outage();double prior=n.latitude();
        assertFalse(n.fix(fix(RETURN-3*S,prior+.001,3),RETURN));
        assertTrue(n.estimated(RETURN));assertEquals(prior,n.latitude(),0);
        assertTrue(n.fix(fix(RETURN,prior+.001,30),RETURN));
        assertTrue(n.estimated(RETURN));assertEquals(prior,n.latitude(),0);
    }
    @Test public void smallOffsetsStillUseTheExistingGentleTransition() {
        var n=outage();double prior=n.latitude(),target=prior+2.0/111320;
        n.fix(fix(RETURN,target,3),RETURN);
        assertEquals(prior,n.latitude(),1e-10);
        n.tick(RETURN+S/2);assertEquals((prior+target)/2,n.latitude(),1e-10);
        n.tick(RETURN+S);assertEquals(target,n.latitude(),1e-10);
    }
}
