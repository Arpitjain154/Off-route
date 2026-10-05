package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;

/** Synthetic geometry in local metres, independent of map graphs and models. */
public class RouteFollowerTest {
    private static final long T=10_000_000_000L;
    private static final double M=111320.0;
    private static double[][] route(double... xy) {
        double[][] points=new double[xy.length/2][2];
        for(int i=0;i<points.length;i++)points[i]=new double[]{xy[2*i]/M,xy[2*i+1]/M};
        return points;
    }
    private static RouteFollower straight() {
        RouteFollower f=new RouteFollower();assertTrue(f.setRoute(route(0,0,20000,0)));
        assertTrue(f.anchor(T,0,0,3,90,90));return f;
    }
    @Test public void healthyGnssNeverProducesAnOverrideAndOutageStartsAtAnchorTime() {
        RouteFollower f=straight();assertNull(f.update(T+1_000_000_000L,false,false,10,90));
        assertFalse(f.active());
        RouteFollower.Estimate p=f.update(T+3_000_000_000L,true,false,10,90);
        assertNotNull(p);assertEquals(30,p.progressMeters(),1e-6);
        assertEquals(30/M,p.longitude(),1e-9);assertEquals(0,p.latitude(),0);
        assertEquals(90,p.bearingDegrees(),1e-6);
    }
    @Test public void eligibilityRequiresNearbyTrustedAnchorAndUsableRoute() {
        RouteFollower f=new RouteFollower();assertFalse(f.anchor(T,0,0,3,90,90));
        assertTrue(f.setRoute(route(0,0,1000,0)));
        assertFalse(f.anchor(T,16/M,0,3,90,90));
        assertNull(f.update(T+3_000_000_000L,true,false,10,90));
        assertTrue(f.anchor(T+1,14/M,0,3,90,90));assertTrue(f.eligible());
        assertFalse(f.anchor(T+2,0,0,21,90,90));assertFalse(f.eligible());
        assertFalse(f.setRoute(new double[][]{{0,0},{Double.NaN,0}}));
        assertFalse(f.setRoute(route(0,0,0,0)));
    }
    @Test public void progressionFollowsCurveInsteadOfChordAndUncertaintyGrows() {
        RouteFollower f=new RouteFollower();f.setRoute(route(0,0,30,0,30,1000));
        f.anchor(T,0,0,3,90,90);
        RouteFollower.Estimate a=f.update(T+2_000_000_000L,true,false,10,90);
        RouteFollower.Estimate b=f.update(T+4_000_000_000L,true,false,10,0);
        assertNotNull(b);assertEquals(30/M,b.longitude(),1e-9);assertEquals(10/M,b.latitude(),1e-9);
        assertEquals(0,b.bearingDegrees(),1e-6);assertEquals(40,b.distanceMeters(),1e-6);
        assertTrue(b.alongUncertaintyMeters()>a.alongUncertaintyMeters());
        assertTrue(b.crossTrackUncertaintyMeters()>=a.crossTrackUncertaintyMeters());
        assertTrue(b.crossTrackUncertaintyMeters()>3);
        RouteFollower.Projection middle=f.positionAt(35);
        assertEquals(30/M,middle.longitude(),1e-9);assertEquals(5/M,middle.latitude(),1e-9);
    }
    @Test public void outageCannotJumpToParallelReturnRoadOrResumeAfterReversal() {
        RouteFollower f=new RouteFollower();f.setRoute(route(0,0,200,0,200,8,0,8));
        assertTrue(f.anchor(T,0,100/M,3,90,90));
        RouteFollower.Estimate p=f.update(T+5_000_000_000L,true,false,10,90);
        assertNotNull(p);assertEquals(150,p.progressMeters(),1e-4);assertEquals(0,p.latitude(),0);
        assertEquals(150/M,p.longitude(),1e-9);
        assertNull(f.update(T+6_000_000_000L,true,false,10,270));assertFalse(f.active());
        assertNull(f.update(T+7_000_000_000L,true,false,10,90));
    }
    @Test public void oppositeCourseCannotSelectFartherParallelSegment() {
        RouteFollower f=new RouteFollower();f.setRoute(route(0,0,200,0,200,8,0,8));
        assertFalse(f.anchor(T,0,100/M,3,270,270));
        assertTrue(f.anchor(T+1,8/M,100/M,3,270,270));
    }
    @Test public void signedGyroDivergenceUsesOnlyLastFiveSeconds() {
        RouteFollower f=straight();
        for(int i=1;i<=10;i++)assertNotNull(f.update(T+i*1_000_000_000L,true,false,10,90+5*i));
        assertNull(f.update(T+11_000_000_000L,true,false,10,151));
        assertNull(f.update(T+12_000_000_000L,true,false,10,90));
        assertTrue(f.anchor(T+13_000_000_000L,0,130/M,3,90,90));
        assertNotNull(f.update(T+14_000_000_000L,true,false,10,90));
    }
    @Test public void oppositeGyroTurnCannotPassByMatchingMagnitude() {
        RouteFollower f=new RouteFollower();f.setRoute(route(0,0,30,0,30,1000));
        f.anchor(T,0,0,3,90,90);assertNull(f.update(T+4_000_000_000L,true,false,10,180));
    }
    @Test public void timeoutAndStopsFallBackUntilNewAnchor() {
        RouteFollower f=straight();assertNotNull(f.update(T+120_000_000_000L,true,false,10,90));
        assertNull(f.update(T+120_000_000_001L,true,false,10,90));
        f=straight();assertNull(f.update(T+1_000_000_000L,true,true,10,90));
        assertNull(f.update(T+2_000_000_000L,true,false,10,90));
        assertNull(straight().update(T+1_000_000_000L,true,false,0,90));
    }
    @Test public void invalidTimeOrSpeedCannotReverseOrAdvanceProgress() {
        RouteFollower f=straight();assertNotNull(f.update(T+1_000_000_000L,true,false,10,90));
        assertNull(f.update(T+500_000_000L,true,false,10,90));
        assertFalse(f.anchor(T-1,0,100/M,3,90,90));
        assertEquals(20,f.update(T+2_000_000_000L,true,false,10,90).progressMeters(),1e-6);
        assertNull(f.update(T+3_000_000_000L,true,false,-1,90));
        assertNull(straight().update(T+1_000_000_000L,true,false,Double.NaN,90));
        assertNull(straight().update(T+1_000_000_000L,true,false,81,90));
        assertNull(straight().update(T+1_000_000_000L,true,false,10,Double.NaN));
    }
    @Test public void replacedOrClearedRouteCannotReuseAnOldAnchor() {
        RouteFollower f=straight();assertNotNull(f.update(T+1_000_000_000L,true,false,10,90));
        f.setRoute(route(0,100,1000,100));assertFalse(f.eligible());
        assertNull(f.update(T+2_000_000_000L,true,false,10,90));f.clear();
        assertNull(f.project(100/M,0,90));
    }
    @Test public void routeEndFallsBackInsteadOfPinningMovingVehicle() {
        RouteFollower f=new RouteFollower();f.setRoute(route(0,0,10,0));f.anchor(T,0,0,3,90,90);
        assertNotNull(f.update(T+1_000_000_000L,true,false,10,90));
        assertNull(f.update(T+2_000_000_000L,true,false,10,90));
    }
    @Test public void identicalRouteRefreshPreservesProgressAndCopiesCallerCoordinates() {
        RouteFollower f=new RouteFollower();double[][] points=route(0,0,20000,0);
        f.setRoute(points);f.anchor(T,0,0,3,90,90);
        assertEquals(10,f.update(T+1_000_000_000L,true,false,10,90).progressMeters(),1e-6);
        assertTrue(f.setRoute(route(0,0,20000,0)));assertTrue(f.active());
        points[1][0]=1;
        assertEquals(20,f.update(T+2_000_000_000L,true,false,10,90).progressMeters(),1e-6);
        assertTrue(f.setRoute(route(0,0,20000,0)));assertTrue(f.eligible());
    }
    @Test public void turnImmediatelyAfterVertexAnchorUsesIncomingCourse() {
        RouteFollower f=new RouteFollower();f.setRoute(route(0,0,30,0,30,1000));
        assertTrue(f.anchor(T,0,30/M,3,90,90));
        assertNotNull(f.update(T+1_000_000_000L,true,false,10,0));
    }}
