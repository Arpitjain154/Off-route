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
    // The old two-second 150 m transition is intentionally replaced by a bounded correction.
    @Test public void largeOffsetBlendsWithoutJumpAtEveryPublishedFrameAndConverges() {
        for(double offset:new double[]{-150,150}) {
            var n=outage();double prior=n.latitude(),target=prior+offset/111320;
            assertTrue(n.fix(fix(RETURN,target,4),RETURN));
            assertEquals("First recovery frame remains continuous",prior,n.latitude(),1e-10);
            assertTrue("Uncertainty includes unresolved GNSS offset",n.uncertainty(RETURN)>149);
            double previous=prior;
            for(int i=1;i<=120;i++) {
                long t=RETURN+i*S/10;
                if(i%10==0)n.fix(fix(t,target,4),t);
                n.tick(t);
                assertTrue("Recovery correction is bounded per 100 ms",Math.abs(n.latitude()-previous)*111320<=1.50001);
                previous=n.latitude();
            }
            assertEquals("Repeated good fixes converge without restarting recovery",target,n.latitude(),1e-10);
            assertEquals(4,n.uncertainty(RETURN+12*S),1e-8);
        }
    }
    @Test public void newGoodFixesDoNotRestartTheRecoveryClock() {
        var n=outage();double initial=n.latitude(),target=initial+150.0/111320;
        n.fix(fix(RETURN,target,4),RETURN);
        for(int i=1;i<=200;i++) {
            long t=RETURN+i*S/10;
            if(i%10==0)n.fix(fix(t,target+i*.5/111320,4),t);
            n.tick(t);
        }
        assertEquals(target+100.0/111320,n.latitude(),1e-10);
        assertEquals(5,n.speed(RETURN+20*S),1e-10);
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
    @Test public void lossDuringBlendResumesFromDisplayedPosition() {
        var n=outage();double prior=n.latitude();n.fix(fix(RETURN,prior+150.0/111320,4),RETURN);
        n.tick(RETURN+S/10);double displayed=n.latitude();n.providerDisabled();
        assertEquals(displayed,n.latitude(),1e-10);
    }
}
