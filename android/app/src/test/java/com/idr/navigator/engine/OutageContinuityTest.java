package com.idr.navigator.engine;
import org.junit.Test;
import static org.junit.Assert.*;
public class OutageContinuityTest {
    private static final long S=1_000_000_000L;
    private LiveNavigation moving() {
        LiveNavigation n=new LiveNavigation();
        for(int i=0;i<=200;i++) {
            long t=S+i*10_000_000L;sample(n,t);
            if(i%100==0)n.fix(new NavigationFusion.Fix(t,23.3+i*.111/111320,77.35,0,3,11.1,.2,0,5),t);
        }
        n.providerDisabled();return n;
    }
    private void sample(LiveNavigation n,long t){n.gravity(t,0,0,9.80665);n.compass(t,0,5);n.imu(new ImuSample(t,0,0,9.80665,0,0,0),true);}
    @Test public void movingOutageTracksForTenMinutesWithHonestGrowingUncertainty() {
        LiveNavigation n=moving();double start=n.latitude(),uncertainty=n.uncertainty(3*S);
        for(int i=1;i<=60000;i++) {
            long t=3*S+i*10_000_000L;sample(n,t);
            if(i%10==0){assertTrue(n.estimated(t));assertTrue(n.trackingAvailable(t));assertTrue(n.speedAvailable(t));assertFalse(n.stopped(t));}
        }
        assertTrue((n.latitude()-start)*111320>5000);
        assertTrue(n.uncertainty(603*S)>uncertainty);
        assertTrue(Double.isFinite(n.uncertainty(603*S)));
    }
    @Test public void invalidSensorsHoldUntilFreshValidSamplesReturn() {
        LiveNavigation n=moving();sample(n,3*S+10_000_000L);double lat=n.latitude();
        n.imu(new ImuSample(3*S+20_000_000L,Double.NaN,0,9.80665,0,0,0),true);
        assertFalse(n.trackingAvailable(3*S+20_000_000L));assertEquals(lat,n.latitude(),0);
        sample(n,3*S+30_000_000L);assertTrue(n.estimated(3*S+30_000_000L));
        n.tick(4*S);assertFalse(n.trackingAvailable(4*S));
    }
}
