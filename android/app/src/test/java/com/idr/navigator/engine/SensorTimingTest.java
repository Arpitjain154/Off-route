package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
/** Independent Android sensor streams need not arrive in global timestamp order. */
public class SensorTimingTest {
 private static final long S=1_000_000_000L;
 private LiveNavigation prepared(){
  var n=new LiveNavigation();n.gravity(S,0,0,9.80665);n.compass(S,0,5);
  n.fix(new NavigationFusion.Fix(S,23.3,77.35,0,3,10,.2,0,5),S);
  n.imu(new ImuSample(S,0,0,9.80665,0,0,0));return n;
 }
 @Test public void slightlyReorderedGravityMustNotBlockTimeoutHandoff(){
  var n=prepared();
  for(int i=1;i<=500;i++){
   long t=S+i*10_000_000L;n.gravity(t+5_000_000L,0,0,9.80665);
   n.imu(new ImuSample(t,0,0,9.80665,0,0,0));
   if(i>200)assertTrue("Fresh independent sensor streams must support INS",n.estimated(t));
  }
  assertEquals("Include travel since the last fix",50,(n.latitude()-23.3)*111320,.1);
  assertTrue(n.trackingAvailable(6*S));assertTrue(n.speedAvailable(6*S));assertEquals(10,n.speed(6*S),.01);
 }
 @Test public void staleGyroscopeMustNotBeReportedAsReadyImu(){
  var n=prepared();n.providerDisabled();
  for(int i=1;i<=100;i++){
   long t=S+i*10_000_000L;n.gravity(t,0,0,9.80665);
   n.imu(new ImuSample(t,0,0,9.80665,0,0,0),false);
  }
  assertFalse(n.trackingAvailable(2*S));
  assertFalse("Accelerometer alone is not a ready IMU",n.imuFresh(2*S));
 }
 @Test public void largeClockSkewAndMissingGravityRemainUnavailable(){
  var n=prepared();n.providerDisabled();n.gravity(S+200_000_000L,0,0,9.80665);
  n.imu(new ImuSample(S+10_000_000L,0,0,9.80665,0,0,0));assertFalse(n.estimated(S+10_000_000L));
  n.imu(new ImuSample(2*S,0,0,9.80665,0,0,0));assertFalse(n.estimated(2*S));
 }
}
