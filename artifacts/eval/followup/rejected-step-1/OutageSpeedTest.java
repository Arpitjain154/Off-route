package com.idr.navigator.engine;

import static org.junit.Assert.*;
import org.junit.Test;

public class OutageSpeedTest {
    @Test public void healthyGnssIsBitIdenticalWithFlagEnabled() {
        NavigationFusion before=new NavigationFusion(),after=new NavigationFusion();
        before.boundedOutageSpeed=false;after.boundedOutageSpeed=true;
        for(int i=0;i<1500;i++) {
            long t=1_000_000_000L+i*10_000_000L;
            for(NavigationFusion n:new NavigationFusion[]{before,after}) {
                n.gravity(t,0,0,9.80665);
                n.imu(new ImuSample(t,.03,.1,9.80665,0,0,.001),true);
                if(i%100==0)n.fix(new NavigationFusion.Fix(t,23+i*.1/111320,77,0,3,10,.2,0,5),t);
            }
            assertArrayEquals(before.filter.p,after.filter.p,0);
            assertArrayEquals(before.filter.v,after.filter.v,0);
            assertArrayEquals(before.filter.q,after.filter.q,0);
            for(int j=0;j<15;j++)assertEquals(before.filter.covarianceDiagonal(j),after.filter.covarianceDiagonal(j),0);
        }
    }

    @Test public void confirmedStopDoesNotAdvanceTowardAnOldMovingAnchor() {
        NavigationFusion n=new NavigationFusion();n.boundedOutageSpeed=true;
        for(int i=0;i<3000;i++) {
            long t=1_000_000_000L+i*10_000_000L;
            n.gravity(t,0,0,9.80665);n.imu(new ImuSample(t,0,0,9.80665,0,0,0),true);
            if(i%100==0)n.fix(new NavigationFusion.Fix(t,23,77,0,3,0,.2,0,5),t);
        }
        assertTrue(n.physics.isStopped(30_990_000_000L));n.providerDisabled();double[] p=n.filter.p.clone();
        for(int i=0;i<1000;i++) {
            long t=31_000_000_000L+i*10_000_000L;
            n.gravity(t,0,0,9.80665);n.imu(new ImuSample(t,0,0,9.80665,0,0,0),true);
        }
        assertEquals(0,n.filter.speed(),.01);assertArrayEquals(p,n.filter.p,.01);
    }

    @Test public void residualAccelerationCannotRunAwayDuringLoss() {
        NavigationFusion n=new NavigationFusion();
        n.boundedOutageSpeed=true;
        for(int i=0;i<200;i++)n.imu(new ImuSample(1_000_000_000L+i*10_000_000L,0,0,9.80665,0,0,0),true);
        long start=3_000_000_000L;
        n.fix(new NavigationFusion.Fix(start,23,77,0,3,10,.2,0,5),start);
        n.providerDisabled();
        for(int i=1;i<=6000;i++) {
            long t=start+i*10_000_000L;
            n.gravity(t,0,0,9.80665);
            n.imu(new ImuSample(t,0,1,9.80665,0,0,0),true);
        }
        assertTrue("Outage speed must stay within 5 m/s of the trusted 10 m/s anchor: "+n.filter.speed(),n.filter.speed()<=15.000001);
        assertTrue(n.filter.uncertainty()>3);
    }
}
