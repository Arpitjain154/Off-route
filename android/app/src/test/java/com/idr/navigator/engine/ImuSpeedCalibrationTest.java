package com.idr.navigator.engine;

import static org.junit.Assert.*;
import org.junit.Test;

public class ImuSpeedCalibrationTest {
    private static final long S=1_000_000_000L;
    private static NavigationFusion.Fix fix(long t,double speed) {
        return new NavigationFusion.Fix(t,23.3,77.35,0,3,speed,.2,0,5);
    }
    private static InertialNavigation calibrated(double offset) {
        InertialNavigation n=new InertialNavigation();
        n.gravity(S,0,0,9.80665);n.compass(S,0,5);n.anchor(fix(S,10),S,10,false,0);
        n.predict(new ImuSample(S,0,offset,9.80665,0,0,0),true,false);
        for(int i=1;i<=1000;i++) {
            long t=S+i*10_000_000L;
            n.gravity(t,0,0,9.80665);n.compass(t,0,5);
            n.predict(new ImuSample(t,0,offset,9.80665,0,0,0),true,false);
            if(i%100==0)n.anchor(fix(t,10),t,10,false,0);
        }
        return n;
    }
    @Test public void gpsCalibratedPositiveOffsetCannotTurnGentleBrakingIntoAcceleration() {
        var n=calibrated(.2);
        for(int i=1;i<=1000;i++) {
            long t=11*S+i*10_000_000L;n.gravity(t,0,0,9.80665);
            n.predict(new ImuSample(t,0,.1,9.80665,0,0,0),false,false);
        }
        assertEquals("0.2 sensor offset minus 0.1 true braking must reduce speed",9,n.speed(),.3);
    }
    @Test public void bothSignsOfOffsetAreRemovedWithoutForcingMovingVehicleToSlow() {
        for(double offset:new double[]{-.2,.2}) {
            var n=calibrated(offset);
            for(int i=1;i<=3000;i++) {
                long t=11*S+i*10_000_000L;n.gravity(t,0,0,9.80665);
                n.predict(new ImuSample(t,0,offset,9.80665,0,0,0),false,false);
            }
            assertEquals("Steady vehicle should not gain or lose speed from learned sensor offset",10,n.speed(),.3);
        }
    }
    @Test public void gpsAccelerationIsNotLearnedAsSensorOffset() {
        var n=calibrated(0);
        for(int i=1;i<=1000;i++) {
            long t=11*S+i*10_000_000L;n.gravity(t,0,0,9.80665);n.compass(t,0,5);
            n.predict(new ImuSample(t,0,.5,9.80665,0,0,0),true,false);
            if(i%100==0)n.anchor(fix(t,10+i*.005),t,10+i*.005,false,0);
        }
        for(int i=1;i<=1000;i++) {
            long t=21*S+i*10_000_000L;n.gravity(t,0,0,9.80665);
            n.predict(new ImuSample(t,0,-.5,9.80665,0,0,0),false,false);
        }
        assertEquals("Actual braking must remain visible after aided acceleration",10,n.speed(),.3);
    }
}
