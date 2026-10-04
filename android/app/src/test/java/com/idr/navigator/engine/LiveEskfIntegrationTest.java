package com.idr.navigator.engine;

import static org.junit.Assert.*;
import org.junit.Test;

public class LiveEskfIntegrationTest {
    private static final long S=1_000_000_000L;
    private static NavigationFusion.Fix fix(long t,double north,double speed) {
        return new NavigationFusion.Fix(t,23.3+north/111320,77.35,0,3,speed,.2,0,5);
    }
    private static void sample(LiveNavigation n,long t) {
        n.gravity(t,0,0,9.80665);n.compass(t,0,5);
        n.imu(new ImuSample(t,0,0,9.80665,0,0,0),true);
    }
    @Test public void livePositionFusesGpsInnovationInsteadOfCopyingReceiverPosition() {
        LiveNavigation n=new LiveNavigation();sample(n,S);n.fix(fix(S,0,10),S);
        for(int i=1;i<=100;i++)sample(n,S+i*10_000_000L);
        n.fix(fix(2*S,16,10),2*S);
        double metres=(n.latitude()-23.3)*111320;
        assertTrue("An aligned live ESKF should combine predicted 10 m and observed 16 m; got "+metres,metres>10&&metres<15.99);
    }
    @Test public void calibrationCannotReuseOneFixOrCallerSuppliedFakeLabels() {
        LiveNavigation n=new LiveNavigation();sample(n,S);n.fix(fix(S,0,10),S);
        for(int i=1;i<=30;i++) {
            long t=S+i*10_000_000L;sample(n,t);
            double claimedSpeed=10+(i%10)*.5;
            n.models(t,claimedSpeed+1,null,null,claimedSpeed,true);
        }
        assertTrue("Only independent accepted receiver fixes may calibrate AI",n.inertial.calibrationSamples()<=1);
        assertEquals(0,n.inertial.aiWeight(2*S),0);
    }
    @Test public void calibratedAiCorrectsTheSameEskfVelocityUsedByLiveNavigation() {
        LiveNavigation n=new LiveNavigation();sample(n,S);n.fix(fix(S,0,10),S);
        double north=0;
        for(int i=1;i<=4000;i++) {
            long t=S+i*10_000_000L;double speed=10+i*.001;
            north+=speed*.01;n.gravity(t,0,0,9.80665);n.compass(t,0,5);
            n.imu(new ImuSample(t,0,.1,9.80665,0,0,0),true);
            if(i%100==0){n.fix(fix(t,north,speed),t);n.models(t,speed+1,null,null,speed,true);}
        }
        assertTrue(n.inertial.aiWeight(41*S)>0);n.providerDisabled();
        long now=41*S+10_000_000L;sample(n,now);
        double before=n.speed(now),variance=n.fusion.filter.covarianceDiagonal(4);
        n.models(now,before+2,null,null,null,false); // offset 1 => a +1 m/s speed observation
        assertTrue(n.fusion.aiActive(now));
        assertTrue(n.speed(now)>before&&n.speed(now)<before+1.01);
        assertEquals(n.fusion.filter.speed(),n.speed(now),1e-10);
        assertTrue(n.fusion.filter.covarianceDiagonal(4)<variance);
        double once=n.speed(now);n.models(now,70,null,null,null,false);
        assertEquals("Same prediction cannot be counted twice",once,n.speed(now),0);
    }
    @Test public void sidewaysMountUsesVehicleDirectionForEskfAcceleration() {
        LiveNavigation n=new LiveNavigation();n.gravity(S,0,0,9.80665);n.compass(S,0,5);
        n.imu(new ImuSample(S,0,0,9.80665,0,0,0));
        n.fix(new NavigationFusion.Fix(S,23.3,77.35,0,3,10,.2,90,5),S);
        n.providerDisabled();
        for(int i=1;i<=200;i++) {
            long t=S+i*10_000_000L;n.gravity(t,0,0,9.80665);
            n.imu(new ImuSample(t,1,0,9.80665,0,0,0),true);
        }
        assertTrue(n.estimated(3*S));assertTrue(n.speed(3*S)>11.5);
        assertEquals(90,n.heading(),2);assertTrue(n.longitude()>77.35);
    }
    @Test public void losingMountStopsAllInertialCorrectionsUntilRealignment() {
        LiveNavigation n=new LiveNavigation();sample(n,S);n.fix(fix(S,0,10),S);
        for(int i=1;i<=100;i++)sample(n,S+i*10_000_000L);
        n.providerDisabled();long moved=2*S+10_000_000L;
        n.gravity(moved,0,9.80665,0);n.tick(moved);
        double lat=n.latitude(),lon=n.longitude();
        for(int i=1;i<=200;i++) {
            long t=moved+i*10_000_000L;n.gravity(t,0,9.80665,0);
            n.imu(new ImuSample(t,0,9.80665,0,0,0,0),true);
        }
        assertFalse(n.trackingAvailable(moved+2*S));
        assertEquals("An invalid mount must hold the last ESKF pose",lat,n.latitude(),1e-12);
        assertEquals(lon,n.longitude(),1e-12);
    }
    @Test public void noisyStationaryGpsStopsEskfVelocityAndPositionTogether() {
        LiveNavigation n=new LiveNavigation();sample(n,S);n.fix(fix(S,0,5),S);
        for(int i=1;i<=2500;i++) {
            long t=S+i*10_000_000L;sample(n,t);
            if(i%100==0)n.fix(fix(t,0,.8),t);
        }
        assertTrue(n.stopped(26*S));assertEquals(0,n.speed(26*S),0);
        assertTrue("The ESKF must share the live stop evidence",n.fusion.physics.isStopped(26*S));
        n.providerDisabled();double lat=n.latitude(),lon=n.longitude();
        for(int i=1;i<=1000;i++)sample(n,26*S+i*10_000_000L);
        assertTrue(n.estimated(36*S));assertEquals(0,n.fusion.filter.speed(),.05);
        assertEquals(lat,n.latitude(),.1/111320);assertEquals(lon,n.longitude(),.1/111320);
    }
    @Test public void fusedSpeedAndHeadingRemainAvailableWithoutGpsDopplerOrCourse() {
        LiveNavigation n=new LiveNavigation();sample(n,S);n.fix(fix(S,0,10),S);
        for(int i=1;i<=700;i++) {
            long t=S+i*10_000_000L;sample(n,t);
            if(i%100==0)n.fix(new NavigationFusion.Fix(t,23.3+i*.1/111320,77.35,0,3,
                Double.NaN,Double.NaN,Double.NaN,Double.NaN),t);
        }
        assertTrue(n.fused(8*S));assertTrue(n.speedAvailable(8*S));assertTrue(n.headingReady(8*S));
        assertEquals(n.fusion.filter.speed(),n.speed(8*S),1e-10);
    }
    @Test public void yawRemountRecalibratesEskfVehicleAxisFromFreshGnss() {
        LiveNavigation n=new LiveNavigation();sample(n,S);n.fix(fix(S,0,10),S);
        for(int i=1;i<=100;i++) {
            long t=S+i*10_000_000L;n.gravity(t,0,0,9.80665);n.compass(t,i*.9,5);
            n.imu(new ImuSample(t,0,0,9.80665,0,0,-Math.PI/2),true);
        }
        n.fix(fix(2*S,10,10),2*S);n.providerDisabled();double before=n.speed(2*S);
        for(int i=1;i<=200;i++) {
            long t=2*S+i*10_000_000L;n.gravity(t,0,0,9.80665);
            n.imu(new ImuSample(t,-1,0,9.80665,0,0,0),true);
        }
        assertTrue(n.estimated(4*S));assertTrue("New phone -X is the vehicle forward axis",n.speed(4*S)>before+1.5);
        assertEquals(0,Math.abs(Eskf.wrapDegrees(n.heading())),3);
    }
}
