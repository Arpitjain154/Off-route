package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
public class VehicleConstraintRegressionTest {
 private static final long S=1_000_000_000L;
 private NavigationFusion.Fix fix(long t,double speed){return new NavigationFusion.Fix(t,23.3,77.35,0,3,speed,.2,0,5);}
 private InertialNavigation prepared(double speed){
  var n=new InertialNavigation();n.gravity(S,0,0,9.80665);n.compass(S,0,5);n.anchor(fix(S,speed),S,speed,false,0);step(n,S,0,0,9.80665);return n;
 }
 private void step(InertialNavigation n,long t,double x,double y,double z){n.gravity(t,0,0,9.80665);n.predict(new ImuSample(t,x,y,z,0,0,0),false,false);}
 @Test public void roadCannotMoveConfirmedStop(){
  var n=prepared(10);n.anchor(fix(S,0),S,0,true,0);step(n,S+10_000_000L,0,0,9.80665);
  double lat=n.latitude(),lon=n.longitude();n.road(S+10_000_000L,lat,lon,lat,lon+.00005,0,.65);
  assertEquals(lat,n.latitude(),0);assertEquals(lon,n.longitude(),0);assertTrue(n.stopped());
 }
 @Test public void delayedRoadFromBeforeNewGpsCannotBeApplied(){
  var n=prepared(10);step(n,S+10_000_000L,0,0,9.80665);n.anchor(fix(S+20_000_000L,10),S+20_000_000L,10,false,0);step(n,S+30_000_000L,0,0,9.80665);
  double lon=n.longitude();n.road(S+10_000_000L,23.3,77.35,23.3,77.35005,0,.65);assertEquals(lon,n.longitude(),0);
 }
 @Test public void vehicleBumpsCannotEnablePedestrianCadenceSpeed(){
  var n=prepared(2);
  for(int i=1;i<=500;i++)step(n,S+i*10_000_000L,0,0,9.80665+(i%60<4?1.2:0));
  assertEquals("Vertical road pulses do not observe vehicle speed",2,n.speed(),.001);
 }
 @Test public void smallIntegratedSpeedDoesNotProveVehicleIsStopped(){
  var n=prepared(2);n.anchor(fix(S,.05),S,.05,false,0);
  for(int i=1;i<=500;i++)step(n,S+i*10_000_000L,0,0,9.80665);
  assertFalse("A quiet IMU plus an uncertain speed estimate cannot prove a stop",n.stopped());
 }
 @Test public void validRoadCorrectionIsLateralBoundedAndDoesNotAlterSpeed(){
  var n=prepared(10);step(n,S+10_000_000L,0,0,9.80665);double lat=n.latitude(),lon=n.longitude();
  n.road(S+10_000_000L,lat,lon,lat+.00002,lon+.00005,0,.65);
  assertEquals(lat,n.latitude(),0);assertEquals(10,n.speed(),0);
  assertEquals(2,(n.longitude()-lon)*111320*Math.cos(Math.toRadians(lat)),.001);
 }
 @Test public void delayedRoadCannotMovePoseAfterImuStreamStops(){
  var n=prepared(10);step(n,S+10_000_000L,0,0,9.80665);double lon=n.longitude();
  n.road(3*S,S+10_000_000L,n.latitude(),lon,n.latitude(),lon+.00005,0,.65);
  assertEquals(lon,n.longitude(),0);
 }
 @Test public void lateralAndVerticalForcesDoNotCreateForwardVelocity(){
  var n=prepared(10);for(int i=1;i<=500;i++)step(n,S+i*10_000_000L,2,0,10.5);
  assertEquals(10,n.speed(),.001);
 }
}
