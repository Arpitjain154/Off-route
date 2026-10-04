package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
public class InertialNavigationTest {
    @Test public void invalidOptionalModelAndMapOutputsCannotPoisonNavigation() {
        var n=prepared(10,0,0);
        n.models(2*SECOND,10,new float[]{0,0,0,Float.NaN},new float[]{0,Float.NaN,0},null,false);
        predict(n,new ImuSample(SECOND+10_000_000L,0,0,9.80665,0,0,0),false,false);
        n.road(SECOND+10_000_000L,23.3,77.35,23.3,77.35,Double.NaN,.8);
        predict(n,new ImuSample(SECOND+20_000_000L,0,0,9.80665,0,0,0),false,false);
        assertTrue(Double.isFinite(n.latitude()+n.longitude()+n.speed()+n.uncertainty()));
    }
    private static final long SECOND=1_000_000_000L;
    private static void predict(InertialNavigation n,ImuSample s,boolean aiding,boolean stopped){n.gravity(s.t(),0,0,9.80665);n.predict(s,aiding,stopped);}
    private static void imu(LiveNavigation n,ImuSample s){n.gravity(s.t(),0,0,9.80665);n.imu(s);}
    private static void imu(EdgeNavigator n,ImuSample s,boolean gyro){n.gravity(s.t(),0,0,9.80665);n.imu(s,gyro);}

    private static NavigationFusion.Fix fix(long t,double speed,double bearing){return new NavigationFusion.Fix(t,23.3,77.35,0,3,speed,.2,bearing,5);}
    private InertialNavigation prepared(double speed,double course,double phoneHeading) {
        var n=new InertialNavigation();n.gravity(SECOND,0,0,9.80665);n.compass(SECOND,phoneHeading,5);
        n.anchor(fix(SECOND,speed,course),SECOND,speed,speed==0,course);
        predict(n,new ImuSample(SECOND,0,0,9.80665,0,0,0),true,speed==0);return n;
    }
    @Test public void straightBlackoutFiftyAndThousandMetres() {
        for(double speed:new double[]{1,1000.0/60}) {
            var n=prepared(speed,0,0);double seconds=speed==1?50:60;
            for(int i=1;i<=seconds*100;i++)predict(n,new ImuSample(SECOND+i*10_000_000L,0,0,9.80665,0,0,0),false,false);
            double distance=(n.latitude()-23.3)*111320,reference=speed*seconds;
            assertEquals(reference,distance,.1);assertTrue(n.available(SECOND+(long)(seconds*SECOND)));
            System.out.println("Ideal straight simulation: distance="+reference+" m, endpoint error="+Math.abs(distance-reference)+" m; zero bias, not physical validation.");
        }
    }
    @Test public void accelerationAndBrakingAreBidirectionalWithPotholeRejection() {
        var n=prepared(10,0,0);
        for(int i=1;i<=500;i++)predict(n,new ImuSample(SECOND+i*10_000_000L,0,1,9.80665,0,0,0),false,false);
        assertTrue(n.speed()>14&&n.speed()<15.2);
        for(int i=501;i<=1000;i++)predict(n,new ImuSample(SECOND+i*10_000_000L,0,-1,9.80665,0,0,0),false,false);
        assertTrue(n.speed()>9.5&&n.speed()<10.5);
        double before=n.speed();predict(n,new ImuSample(SECOND+1001*10_000_000L,0,0,40,0,0,0),false,false);
        assertEquals(before,n.speed(),.05);
    }
    @Test public void turnsDoNotApplyFalseSpeedDecayAndMountCanBeSideways() {
        var n=prepared(10,90,0);
        for(int i=1;i<=100;i++)predict(n,new ImuSample(SECOND+i*10_000_000L,1,0,9.80665,0,0,0),false,false);
        assertTrue(n.speed()>10.7);
        double before=n.speed();
        for(int i=101;i<=1100;i++)predict(n,new ImuSample(SECOND+i*10_000_000L,0,0,9.80665,0,0,-Math.PI/20),false,false);
        assertEquals(180,n.heading(),.1);assertEquals(before,n.speed(),.25);
    }
    @Test public void confirmedStopSurvivesOutageAndReleasesOnAcceleration() {
        var n=prepared(10,0,0);n.anchor(fix(SECOND,0,0),SECOND,0,true,0);
        for(int i=1;i<=6000;i++)predict(n,new ImuSample(SECOND+i*10_000_000L,.005*Math.sin(i),.005*Math.cos(i),9.80665,0,0,0),false,false);
        assertEquals(0,n.speed(),0);assertEquals(23.3,n.latitude(),0);
        for(int i=6001;i<=6200;i++)predict(n,new ImuSample(SECOND+i*10_000_000L,0,1,9.80665,0,0,0),false,false);
        assertTrue(n.speed()>1.5);
    }
    @Test public void uncalibratedOrStaleSensorsAreNeverClaimedAvailable() {
        var n=new InertialNavigation();n.anchor(fix(SECOND,10,0),SECOND,10,false,0);
        predict(n,new ImuSample(2*SECOND,0,0,9.80665,0,0,0),false,false);assertFalse(n.available(2*SECOND));
        var ready=prepared(10,0,0);assertFalse(ready.available(3*SECOND));
    }
    @Test public void cachedZeroGpsDuringLaunchCannotBecomeAccelerometerBias() {
        var n=prepared(0,0,0);
        for(int i=1;i<=500;i++)predict(n,new ImuSample(SECOND+i*10_000_000L,0,1,9.80665,0,0,0),true,true);
        n.gravity(7*SECOND,0,0,9.80665);n.compass(7*SECOND,0,5);n.anchor(fix(7*SECOND,5,0),7*SECOND,5,false,0);
        predict(n,new ImuSample(7*SECOND,0,0,9.80665,0,0,0),false,false);
        for(int i=1;i<=1000;i++)predict(n,new ImuSample(7*SECOND+i*10_000_000L,0,0,9.80665,0,0,0),false,false);
        assertEquals(5,n.speed(),.3);
    }
    @Test public void aiMustEarnTrustAcrossChangingGnssSpeeds() {
        var n=prepared(10,0,0);
        for(int i=0;i<40;i++)n.models(SECOND+i*SECOND,20,new float[]{1,0,0,0,0},new float[]{1,0,0},10.0,true);
        assertEquals(0,n.aiWeight(40*SECOND),0);
        n=prepared(10,0,0);
        for(int i=40;i<120;i++){double speed=10+(i%10)*.5;n.models(SECOND+i*SECOND,speed+1,new float[]{1,0,0,0,0},new float[]{1,0,0},speed,true);}
        assertTrue(n.aiWeight(120*SECOND)>0);
        n.models(121*SECOND,Double.NaN,null,null,null,false);assertEquals(0,n.aiWeight(121*SECOND),0);
        n=prepared(10,0,0);
        for(int i=0;i<80;i++)n.models(SECOND+i*SECOND,12,null,null,10+(i%8)*.5,true);
        assertEquals("A constant prediction must not pass on changing GPS speeds",0,n.aiWeight(80*SECOND),0);
    }
    @Test public void liveOutputMovesDuringLossAndReconcilesWithoutTeleport() {
        var live=new LiveNavigation();live.gravity(SECOND,0,0,9.80665);live.compass(SECOND,0,5);
        live.fix(fix(SECOND,10,0),SECOND);imu(live,new ImuSample(SECOND,0,0,9.80665,0,0,0));live.providerDisabled();
        for(int i=1;i<=500;i++)imu(live,new ImuSample(SECOND+i*10_000_000L,0,0,9.80665,0,0,0));
        assertTrue(live.estimated(6*SECOND));assertTrue(live.speedAvailable(6*SECOND));assertTrue(live.latitude()>23.3004);
        double previous=live.latitude();var recovered=new NavigationFusion.Fix(6*SECOND,previous+.0002,77.35,0,3,12,.2,0,5);
        live.fix(recovered,6*SECOND);assertEquals(previous,live.latitude(),1e-9);
        assertEquals("Speed recovery starts continuously",10,live.speed(6*SECOND),.1);
        live.tick(8*SECOND);assertEquals(12,live.speed(8*SECOND),.1);
        live.tick(14*SECOND);assertTrue(Double.isFinite(live.latitude()));
    }
    @Test public void remountClearsLearnedModelCalibration() {
        var n=prepared(10,0,0);
        for(int i=0;i<40;i++){double s=10+(i%10)*.5;n.models(SECOND+i*SECOND,s+1,null,null,s,true);}
        assertTrue(n.aiWeight(40*SECOND)>0);
        n.gravity(40*SECOND,0,0,9.80665);n.gravity(40*SECOND+10_000_000L,0,9.80665,0);
        assertFalse(n.aligned());assertEquals(0,n.calibrationSamples());
        for(int i=0;i<40;i++)n.models(41*SECOND+i*SECOND,12,null,null,10+(i%10)*.5,true);
        assertEquals(0,n.aiWeight(80*SECOND),0);
    }
    @Test public void slowPhoneTiltInvalidatesMountUntilFreshGpsRealignsIt() {
        var n=prepared(10,0,0);
        for(int i=1;i<=300;i++) {
            long t=SECOND+i*10_000_000L;double angle=Math.toRadians(i*.1);
            double y=9.80665*Math.sin(angle),z=9.80665*Math.cos(angle);
            n.gravity(t,0,y,z);
            n.predict(new ImuSample(t,0,y,z,0,0,0),false,false);
        }
        assertFalse("A slow cumulative tilt must invalidate the old phone mount",n.aligned());
        assertFalse(n.available(4*SECOND));
        double lat=n.latitude(),lon=n.longitude(),speed=n.speed();
        for(int i=301;i<=500;i++) {
            long t=SECOND+i*10_000_000L;
            n.gravity(t,0,4.903325,8.492808026);
            n.predict(new ImuSample(t,0,6.903325,8.492808026,0,0,0),false,false);
        }
        assertEquals("Unaligned phone acceleration cannot invent travel",lat,n.latitude(),0);
        assertEquals(lon,n.longitude(),0);assertEquals(speed,n.speed(),0);
        n.gravity(7*SECOND,0,4.903325,8.492808026);n.compass(7*SECOND,0,5);
        n.anchor(fix(7*SECOND,10,0),7*SECOND,10,false,0);
        n.gravity(7*SECOND+10_000_000L,0,4.903325,8.492808026);
        n.predict(new ImuSample(7*SECOND+10_000_000L,0,4.903325,8.492808026,0,0,0),true,false);
        n.gravity(7*SECOND+20_000_000L,0,4.903325,8.492808026);
        n.predict(new ImuSample(7*SECOND+20_000_000L,0,4.903325,8.492808026,0,0,0),true,false);
        assertTrue("Good GPS can calibrate the new mounting orientation",n.available(7*SECOND+20_000_000L));
        assertTrue(n.latitude()>23.3);assertEquals(10,n.speed(),.01);
    }
    @Test public void vehicleYawTurnPreservesMountAlignmentAndSpeed() {
        var n=prepared(10,0,0);
        for(int i=1;i<=500;i++) {
            long t=SECOND+i*10_000_000L;
            predict(n,new ImuSample(t,Math.PI,0,9.80665,0,0,-Math.PI/10),false,false);
        }
        assertTrue(n.available(6*SECOND));assertEquals(90,n.heading(),.01);
        assertEquals("Centripetal acceleration is not forward acceleration",10,n.speed(),.001);
    }
    @Test public void edgeApiProcessesTwoHundredHzAndRejectsStaleGyro() {
        var edge=new EdgeNavigator();edge.gravity(SECOND,0,0,9.80665);edge.compass(SECOND,0,5);
        assertTrue(edge.gnss(fix(SECOND,10,0),SECOND));imu(edge,new ImuSample(SECOND,0,0,9.80665,0,0,0),true);edge.gnssUnavailable();
        for(int i=1;i<=12000;i++)imu(edge,new ImuSample(SECOND+i*5_000_000L,0,0,9.80665,0,0,0),true);
        var p=edge.position(61*SECOND);assertTrue(p.available());assertTrue(p.estimated());
        assertEquals(600,(p.latitude()-23.3)*111320,.1);
        for(int i=1;i<=200;i++)imu(edge,new ImuSample(61*SECOND+i*5_000_000L,0,0,9.80665,0,0,0),false);
        assertFalse(edge.position(62*SECOND).available());
    }
}
