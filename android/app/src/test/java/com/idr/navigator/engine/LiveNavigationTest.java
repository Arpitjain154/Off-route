package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;

public class LiveNavigationTest {
    private NavigationFusion.Fix fix(long t,double lat,double lon,double speed,double course){return new NavigationFusion.Fix(t,lat,lon,0,3,speed,.25,course,5);}
    @Test public void turnsKeepDopplerSpeedAndMovingPosition() {
        LiveNavigation n=new LiveNavigation();
        for(int i=0;i<120;i++) {
            long t=1_000_000_000L+i*1_000_000_000L;double angle=i*Math.PI/30;
            double lat=23.3+Math.sin(angle)*.001,lon=77.35+Math.cos(angle)*.001;
            assertTrue(n.fix(fix(t,lat,lon,10,(i*6)%360),t));
            assertEquals(10,n.speed(t),.001);assertEquals(lat,n.latitude(),0);assertEquals(lon,n.longitude(),0);
            assertTrue(Math.abs(Eskf.wrapDegrees(n.heading()-(i*6)%360))<1);
        }
    }
    @Test public void signalLossShowsUnavailableAndRecoveryCanAccelerateOrDecelerate() {
        LiveNavigation n=new LiveNavigation();long t=10_000_000_000L;
        n.fix(fix(t,23.3,77.35,8,90),t);assertTrue(n.speedAvailable(t));
        assertFalse(n.speedAvailable(t+3_000_000_000L));double lon=n.longitude();
        n.providerDisabled();assertFalse(n.speedAvailable(t));
        for(int i=1;i<=500;i++)n.imu(new ImuSample(t+i*10_000_000L,3,0,9.8,0,0,0));
        assertEquals(lon,n.longitude(),0);
        t+=6_000_000_000L;n.fix(fix(t,23.3,77.3501,15,90),t);assertEquals(15,n.speed(t),.001);
        t+=1_000_000_000L;n.fix(fix(t,23.3,77.3502,5,90),t);assertTrue(n.speed(t)<7);
    }
    @Test public void noisyStationaryGnssRemainsZeroForMinutesButSmoothMotionIsNotStopped() {
        for(double speed:new double[]{.8,8}) {
            LiveNavigation n=new LiveNavigation();
            for(int i=0;i<12000;i++) {
                long t=1_000_000_000L+i*10_000_000L;
                n.imu(new ImuSample(t,.006*Math.sin(i),0,9.80665,0,0,0));
                if(i%100==0)n.fix(fix(t,23.3+Math.sin(i)*.00001,77.35+Math.cos(i)*.00001,speed,0),t);
                if(i>1200){assertEquals(speed<1,n.stopped(t));assertEquals(speed<1?0:speed,n.speed(t),.001);}
            }
        }
    }
    @Test public void compassIsOnlyUsedWhenAccurateAndDoesNotOverrideMovingCourse() {
        LiveNavigation n=new LiveNavigation();long t=1_000_000_000L;
        n.compass(t,90,60);assertFalse(n.headingReady(t));
        n.compass(t+1,359,10);assertEquals(359,n.heading(),0);
        for(int i=1;i<=30;i++)n.compass(t+i*20_000_000L,1,10);
        assertTrue(Math.abs(Eskf.wrapDegrees(n.heading()-1))<.1);
        for(int degree:new int[]{90,180,0}) {
            t+=2_000_000_000L;n.fix(fix(t,23.3,77.35,10,degree),t);
            n.compass(t+1,270,5);assertTrue(Math.abs(Eskf.wrapDegrees(n.heading()-degree))<1);
        }
    }
    @Test public void invalidStaleAndTeleportFixesCannotMoveTheMarker() {
        LiveNavigation n=new LiveNavigation();long t=3_000_000_000L;
        assertTrue(n.fix(fix(t,23.3,77.35,8,90),t));
        assertFalse(n.fix(fix(t-1,23.31,77.35,8,90),t));
        assertFalse(n.fix(fix(t+1_000_000_000L,24,78,8,90),t+1_000_000_000L));
        assertEquals(23.3,n.latitude(),0);
    }
}
