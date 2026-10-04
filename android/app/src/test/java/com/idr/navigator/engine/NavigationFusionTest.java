package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;

public class NavigationFusionTest {
    @Test public void delayedGpsUsesMeasuredVelocityToReachTheCurrentEpoch() {
        NavigationFusion n=warm();long t=3_000_000_000L;n.fix(fix(t,23.24,77.4,10,90),t);
        n.filter.p[0]=20;
        double lon=77.4+Math.toDegrees(10/(6378137*Math.cos(Math.toRadians(23.24))));
        assertTrue(n.fix(fix(t+1_000_000_000L,23.24,lon,10,90),t+2_000_000_000L));
        assertEquals(20,n.filter.p[0],.05);
    }
    @Test public void acceptedPositionChangesCanAlignWhenSpeedAccuracyIsAbsent() {
        NavigationFusion n=warm();long t=3_000_000_000L;
        for(int i=0;i<=8;i++) {
            long stamp=t+i*1_000_000_000L;
            n.fix(new NavigationFusion.Fix(stamp,23.24,77.4+i*10/(111320*Math.cos(Math.toRadians(23.24))),0,2,
                10,Double.NaN,90,Double.NaN),stamp);
        }
        assertTrue(n.aligned());assertEquals(90,n.filter.heading(),1);assertEquals(10,n.filter.speed(),1);
    }
    @Test public void gravityAdvisorCorrectsTiltWithoutInventingYaw() {
        Eskf f=new Eskf();f.initialize(23,77,0);f.align(new double[]{1,0,9.75},73);
        for(int i=0;i<50;i++)assertTrue(f.update(f.gravity(new double[]{0,0,9.80665},.2),.8));
        double[] world=Eskf.rotate(f.q,new double[]{0,0,9.80665});
        assertEquals(0,world[0],.02);assertEquals(0,world[1],.02);assertEquals(73,f.heading(),.2);
    }
    @Test public void versionOrderingPreservesNewerOtaAndAllowsBundledUpgrades() {
        assertTrue(ModelVersion.compare("1.1.0","1.0.0")>0);
        assertTrue(ModelVersion.compare("1.1.0","2.0.0")<0);
        assertTrue(ModelVersion.compare("1.1.0","1.1.0-rc.2")>0);
        assertTrue(ModelVersion.compare("1.1.0-rc.10","1.1.0-rc.2")>0);
        assertEquals(0,ModelVersion.compare("1.1.0+build.9","1.1.0"));
    }
    @Test public void threeConsistentFixesRecoverFromLargeOutageDrift() {
        NavigationFusion n=warm();long t=3_000_000_000L;
        n.fix(fix(t,23.24,77.4,10,90),t);
        n.filter.p[0]=2000;
        for(int i=1;i<=3;i++)n.fix(fix(t+i*1_000_000_000L,23.24,77.4,10,90),t+i*1_000_000_000L);
        assertEquals(t+3_000_000_000L,n.lastFix());
        assertEquals(0,n.filter.p[0],5);assertEquals(10,n.filter.speed(),.1);
    }
    private NavigationFusion warm() {
        NavigationFusion n=new NavigationFusion();
        for(int i=0;i<200;i++) n.imu(new ImuSample(1_000_000_000L+i*10_000_000L,0,0,9.80665,0,0,0),true);
        return n;
    }
    private NavigationFusion.Fix fix(long t,double lat,double lon,double speed,double heading) {
        return new NavigationFusion.Fix(t,lat,lon,0,3,speed,.2,heading,5);
    }
    @Test public void gpsInitializesHeadingThenContinuesThroughOutage() {
        NavigationFusion n=warm();long t=3_000_000_000L;
        assertTrue(n.fix(fix(t,23.24,77.4,10,90),t));
        assertTrue(n.aligned());
        for(int i=0;i<=3000;i++) n.imu(new ImuSample(t+i*10_000_000L,0,0,9.80665,0,0,0),true);
        assertEquals(300,n.filter.p[0],2);assertEquals(0,n.filter.p[1],.1);
        assertEquals(n.filter.speed(),n.speed(t+30_000_000_000L),0);
    }
    @Test public void staleAndRejectedFixesDoNotBecomeLiveGps() {
        NavigationFusion n=warm();long t=3_000_000_000L;
        assertTrue(n.fix(fix(t,23.24,77.4,10,0),t));
        assertFalse(n.fix(fix(t+1,24,78,10,0),t+1));
        assertEquals(t,n.lastFix());
        assertFalse(n.fix(fix(t+2,23.24,77.4,10,0),t+3_000_000_000L));
        assertFalse(n.fix(fix(t-1,23.24,77.4,10,0),t));
    }
    @Test public void courseCorrectionWrapsNorthAndWorksForPortraitMount() {
        Eskf f=new Eskf();f.initialize(23,77,0);f.align(new double[]{0,9.80665,0},359);
        for(int i=0;i<20;i++) assertTrue(f.update(f.course(2,5),.8));
        assertEquals(2,f.heading(),1);
        double[] gravity=Eskf.rotate(f.q,new double[]{0,9.80665,0});
        assertEquals(9.80665,gravity[2],.02);
    }
    @Test public void duplicateAndInvalidImuCannotCorruptIntegrationClock() {
        Eskf f=new Eskf();f.initialize(23,77,0);f.v[1]=10;
        f.predict(new ImuSample(1_000_000_000L,0,0,9.80665,0,0,0));
        f.predict(new ImuSample(900_000_000L,0,0,9.80665,0,0,0));
        f.predict(new ImuSample(1_100_000_000L,0,0,9.80665,0,0,0));
        f.predict(new ImuSample(1_150_000_000L,Double.NaN,0,9.80665,0,0,0));
        assertEquals(1,f.p[1],.0001);
        assertFalse(f.update(Measurement.direct(0,new double[]{Double.NaN,0},f.p,1),1));
        assertEquals(1,f.p[1],.0001);
    }
    @Test public void missingGyroscopeDoesNotIntegrateAnInventedZeroRate() {
        NavigationFusion n=warm();long t=3_000_000_000L;
        n.fix(fix(t,23.24,77.4,10,0),t);
        for(int i=0;i<200;i++)n.imu(new ImuSample(t+i*10_000_000L,3,0,9.80665,0,0,0),false);
        assertEquals(0,n.filter.p[0],.0001);
        assertTrue(n.diagnostic.contains("GPS only"));
    }
    @Test public void featureWindowResetsAcrossMissingSensorData() {
        FeatureBuffer f=new FeatureBuffer();
        for(int i=0;i<300;i++)f.add(new ImuSample(1_000_000_000L+i*10_000_000L,0,0,9.8,0,0,0));
        assertTrue(f.ready());f.add(new ImuSample(5_000_000_000L,0,0,9.8,0,0,0));assertFalse(f.ready());
    }
}
