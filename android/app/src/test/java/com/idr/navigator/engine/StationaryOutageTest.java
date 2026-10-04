package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
public class StationaryOutageTest {
 static final long S=1_000_000_000L;
 static InertialNavigation prepared(){var n=new InertialNavigation();n.gravity(S,0,0,9.80665);n.compass(S,0,5);n.anchor(new NavigationFusion.Fix(S,23.3,77.35,0,3,5,.2,0,5),S,5,false,0);n.predict(new ImuSample(S,0,0,9.80665,0,0,0),true,false);return n;}
 @Test public void confirmedStopFromDetectorSurvivesResidualGravityDuringBlackout(){
  var n=prepared();n.anchor(new NavigationFusion.Fix(2*S,23.3,77.35,0,3,0,.2,0,5),2*S,0,true,0);
  for(int i=1;i<=12000;i++){long t=2*S+i*10_000_000L;n.gravity(t,0,0,9.80665);n.predict(new ImuSample(t,0,.28,9.80665,0,0,0),false,true);}
  double drift=Math.abs(n.latitude()-23.3)*111320;
  System.out.println("Confirmed stationary outage: drift="+drift+" m, speed="+n.speed()+" m/s, uncertainty="+n.uncertainty()+" m");
  assertEquals("Confirmed detector stop must control velocity",0,n.speed(),1e-9);assertEquals(0,drift,.01);assertTrue("A held position must not accumulate moving-speed uncertainty",n.uncertainty()<10);
 }
 @Test public void fullLivePipelineHoldsConfirmedStationaryPhoneAndReleasesForLaunch(){
  var n=new LiveNavigation();
  for(int i=0;i<=15000;i++){
   long t=S+i*10_000_000L;n.gravity(t,0,0,9.80665);n.compass(t,0,5);
   if(i<=2000&&i%100==0)n.fix(new NavigationFusion.Fix(t,23.3,77.35,0,3,i==0?5:0,.2,0,5),t);
   if(i==2000)n.providerDisabled();
   n.imu(new ImuSample(t,0,.28,9.80665,0,0,0));
  }
  // A covariance-weighted GNSS/ZUPT estimate need not copy the receiver coordinate exactly.
  assertTrue(n.estimated(151*S));assertEquals(0,n.speed(151*S),.001);assertEquals(23.3,n.latitude(),.1/111320);assertTrue(n.uncertainty(151*S)<10);
  for(int i=1;i<=200;i++){long t=151*S+i*10_000_000L;n.gravity(t,0,0,9.80665);n.imu(new ImuSample(t,0,1.2,9.80665,0,0,0));}
  assertTrue("Actual motion releases the stop",n.speed(153*S)>1);assertTrue(n.latitude()>23.3);
 }
 @Test public void stationaryOffsetDoesNotMaskAGentleLaunch(){
  for(double baseline:new double[]{0,.28}){
   var n=new LiveNavigation();
   for(int i=0;i<=3000;i++){long t=S+i*10_000_000L;n.gravity(t,0,0,9.80665);n.compass(t,0,5);if(i<=1500&&i%100==0)n.fix(new NavigationFusion.Fix(t,23.3,77.35,0,3,i==0?5:0,.2,0,5),t);if(i==1500)n.providerDisabled();n.imu(new ImuSample(t,0,baseline,9.80665,0,0,0));}
   assertEquals(0,n.speed(31*S),.001);
   for(int i=1;i<=200;i++){long t=31*S+i*10_000_000L;n.gravity(t,0,0,9.80665);n.imu(new ImuSample(t,0,baseline+.3,9.80665,0,0,0));}
   assertFalse(n.stopped(33*S));assertTrue(n.speed(33*S)>.2);
  }
 }
 @Test public void predictionGapMustNotIntegrateGravityAccumulatedWhileSensorWasMissing(){
  var n=prepared();double before=n.latitude();
  n.predict(new ImuSample(3*S,0,2,9.80665,0,0,0),false,false);
  assertFalse("Stale gravity cannot support available tracking",n.available(3*S));assertEquals(before,n.latitude(),0);
 }
 @Test public void quietMovingVehicleStillAdvancesWithoutFalseStop(){
  var n=prepared();for(int i=1;i<=3000;i++){long t=S+i*10_000_000L;n.gravity(t,0,0,9.80665);n.predict(new ImuSample(t,0,0,9.80665,0,0,0),false,false);}
  assertEquals(5,n.speed(),.001);assertEquals(150,(n.latitude()-23.3)*111320,.1);assertFalse(n.stopped());
 }
}
