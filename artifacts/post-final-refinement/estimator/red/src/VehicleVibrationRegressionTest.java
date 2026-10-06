package com.idr.navigator.engine;

import static org.junit.Assert.*;
import org.junit.Test;

/** Deterministic vehicle physics regressions, not measured navigation accuracy. */
public class VehicleVibrationRegressionTest {
    private static final long SECOND=1_000_000_000L;
    private static InertialNavigation prepared(double speed) {
        var n=new InertialNavigation();n.gravity(SECOND,0,0,9.80665);n.compass(SECOND,0,5);
        n.anchor(new NavigationFusion.Fix(SECOND,23.3,77.35,0,3,speed,.2,0,5),SECOND,speed,false,0);
        step(n,SECOND,0,false);return n;
    }
    private static void step(InertialNavigation n,long t,double longitudinal,boolean stopped) {
        n.gravity(t,0,0,9.80665);
        n.predict(new ImuSample(t,0,longitudinal,9.80665,0,0,0),false,stopped);
    }
    @Test public void zeroMeanVibrationCannotCreatePersistentBraking() {
        // +/-7 is below the existing shock gate, but above the positive limit.
        // Exercise both sampling rates and both phases to avoid endpoint cancellation.
        for(int hz:new int[]{65,100,121,200})for(int phase:new int[]{-1,1}) {
            var n=prepared(30);
            for(int i=1;i<=20*hz;i++)step(n,SECOND+Math.round(i*1e9/hz),phase*(i%2==0?7:-7),false);
            assertEquals("Zero-mean vibration at "+hz+" Hz must not remove vehicle speed",30,n.speed(),.12);
        }
    }
    @Test public void sustainedRealBrakingIsNotSmoothedAway() {
        var n=prepared(30);
        for(int i=1;i<=200;i++)step(n,SECOND+i*10_000_000L,-6,false);
        assertTrue("Two seconds of -6 m/s2 must remove approximately 11 m/s",n.speed()>18.9&&n.speed()<19.2);
        assertEquals(-6,n.diagnostics().acceleration(),.001);
    }
    @Test public void appliedAccelerationRetainsExistingVehicleLimits() {
        for(double input:new double[]{7,-10}) {
            var n=prepared(30);
            for(int i=1;i<=300;i++) {
                step(n,SECOND+i*10_000_000L,input,false);
                assertTrue(n.diagnostics().acceleration()<=5);
                assertTrue(n.diagnostics().acceleration()>=-8);
            }
            assertEquals(input>0?5:-8,n.diagnostics().acceleration(),.001);
        }
    }
    @Test public void confirmedStopClearsPriorAccelerationMemory() {
        var n=prepared(30);
        for(int i=1;i<=200;i++)step(n,SECOND+i*10_000_000L,7,false);
        step(n,SECOND+201*10_000_000L,0,true);
        assertEquals(0,n.speed(),0);assertEquals(0,n.diagnostics().acceleration(),0);
        step(n,SECOND+202*10_000_000L,.3,false);
        assertTrue("Restart must begin with the new acceleration, without the old filter tail",n.speed()<.001);
    }
    @Test public void shockRejectionStillCannotCreateSpeed() {
        var n=prepared(30);
        for(int i=1;i<=200;i++)step(n,SECOND+i*10_000_000L,i%2==0?30:-30,false);
        assertEquals(30,n.speed(),0);
    }
}
