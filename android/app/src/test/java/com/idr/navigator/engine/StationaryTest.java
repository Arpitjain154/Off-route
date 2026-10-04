package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;

public class StationaryTest {
    @Test public void stopWorksBeforeHeadingAlignmentAndRejectsSmoothMovingCar() {
        for (double speed : new double[]{0,10}) {
            Eskf f=new Eskf();f.initialize(23.2,77.4,0);f.v[0]=1.5;
            PhysicsAdvisor p=new PhysicsAdvisor(f);
            for(int i=0;i<500;i++) {
                long t=1_000_000_000L+i*10_000_000L;
                if(i%100==0)p.gpsSpeed(t,speed,.1);
                p.observe(new ImuSample(t,.01,0,9.80665,0,0,0));
                if(i%10==0) f.update(p.provide(t),p.trustScore());
            }
            assertEquals(speed==0,p.isStopped(5_990_000_000L));
            if(speed==0)assertTrue(f.speed()<.01);
            else assertEquals(1.5,f.speed(),.01);
        }
    }
    @Test public void missingSpeedCannotDeclareAStopAndMotionReleasesIt() {
        StationaryDetector d=new StationaryDetector();
        for(int i=0;i<500;i++)d.observe(new ImuSample(1_000_000_000L+i*10_000_000L,0,0,9.80665,0,0,0));
        assertFalse(d.isStopped(5_990_000_000L));
        for(int i=500;i<800;i++) {
            long t=1_000_000_000L+i*10_000_000L;
            if(i%100==0)d.gps(t,0,.1);
            d.observe(new ImuSample(t,0,0,9.80665,0,0,0));
        }
        assertTrue(d.isStopped(8_990_000_000L));
        d.gps(9_000_000_000L,3,.1);
        assertFalse(d.isStopped(9_000_000_000L));
    }
    @Test public void accurateGpsDoesNotNeedRawMeasurementSupportOrAltitude() {
        Eskf f=new Eskf();f.initialize(23.2,77.4,0);
        GpsAdvisor gps=new GpsAdvisor(f);
        gps.fix(1_000_000_000L,23.20001,77.40001,1000,5);
        assertTrue(gps.trustScore()>.5);
        assertEquals(2,gps.provide(1_000_000_000L).residual().numRows());
    }
    @Test public void horizontalAccelerationReleasesStopDuringGpsDropout() {
        StationaryDetector d=new StationaryDetector();
        for(int i=0;i<400;i++) {
            long t=1_000_000_000L+i*10_000_000L;
            if(i%100==0)d.gps(t,0,.1);
            d.observe(new ImuSample(t,0,0,9.80665,0,0,0));
        }
        assertTrue(d.isStopped(4_990_000_000L));
        d.observe(new ImuSample(5_000_000_000L,1,0,9.80665,0,0,0));
        assertFalse(d.isStopped(5_000_000_000L));
    }
}
