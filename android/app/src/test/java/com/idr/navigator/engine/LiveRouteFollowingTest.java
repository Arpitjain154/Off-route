package com.idr.navigator.engine;
import org.junit.Test;
import static org.junit.Assert.*;
public class LiveRouteFollowingTest {
 private static final long S=1_000_000_000L;
 private static void sample(LiveNavigation n,long t){n.gravity(t,0,0,9.80665);n.compass(t,0,5);n.imu(new ImuSample(t,0,0,9.80665,0,0,0),true);}
 private static LiveNavigation prepare(boolean enabled){
  LiveNavigation n=new LiveNavigation();n.routeFollow=enabled;
  n.setActiveRoute(new double[][]{{77.35,23.3},{77.35,23.35}});
  for(int i=0;i<=300;i++){long t=S+i*10_000_000L;sample(n,t);if(i%100==0)n.fix(new NavigationFusion.Fix(t,23.3+i*.1/111320,77.35,0,3,10,.2,0,5),t);}
  return n;
 }
 @Test public void activeRouteIsOutageOnlyAndFallsBackContinuouslyAfter120Seconds(){
  LiveNavigation n=prepare(true),off=prepare(false);
  assertEquals(n.latitude(),off.latitude(),0);assertEquals(n.longitude(),off.longitude(),0);assertEquals(n.heading(),off.heading(),0);
  assertFalse(n.followingRoute());n.providerDisabled();double start=n.latitude();
  for(int i=1;i<=12000;i++)sample(n,4*S+i*10_000_000L);
  assertTrue(n.followingRoute());assertEquals(77.35,n.longitude(),1e-10);
  assertTrue((n.latitude()-start)*111320>1100);assertTrue(n.alongTrackUncertainty(124*S)>3);
  double before=n.latitude();sample(n,124*S+10_000_000L);assertFalse(n.followingRoute());
  assertEquals(before,n.latitude(),.2/111320);assertTrue(n.estimated(124*S+10_000_000L));
 }
 @Test public void recoveryAlongABendDoesNotDrawADiagonalOffTheRoute(){
  LiveNavigation n=prepare(true);
  n.setActiveRoute(new double[][]{{77.35,23.3},{77.35,23.301},{77.352,23.301}});
  // A new trusted fix anchors the replacement route.
  sample(n,4*S+10_000_000L);n.fix(new NavigationFusion.Fix(4*S+10_000_000L,23.3+30.1/111320,77.35,0,3,10,.2,0,5),4*S+10_000_000L);
  n.providerDisabled();
  for(int i=2;i<=200;i++)sample(n,4*S+i*10_000_000L);
  assertTrue(n.followingRoute());
  double targetLon=77.35+30/(111320*Math.cos(Math.toRadians(23.301)));
  double beforeLat=n.latitude(),beforeLon=n.longitude();
  for(int i=0;i<=100;i++) {
   long t=6*S+i*100_000_000L;
   n.fix(new NavigationFusion.Fix(t,23.301,targetLon,0,3,10,.2,90,5),t);n.tick(t);
   assertTrue("Recovery follows one of the two route legs at "+i+": "+n.latitude()+","+n.longitude(),Math.abs(n.longitude()-77.35)<1e-9||Math.abs(n.latitude()-23.301)<1e-9);
   double jump=Math.hypot((n.latitude()-beforeLat)*111320,(n.longitude()-beforeLon)*111320*Math.cos(Math.toRadians(n.latitude())));
   assertTrue("No icon snap",jump<=5.00001);beforeLat=n.latitude();beforeLon=n.longitude();
  }
 }
 @Test public void sensorGapCannotCatchUpUnobservedRouteDistance(){
  LiveNavigation n=prepare(true);n.providerDisabled();sample(n,4*S+10_000_000L);
  double before=n.latitude();n.tick(9*S);assertFalse(n.trackingAvailable(9*S));
  sample(n,9*S+10_000_000L);sample(n,9*S+20_000_000L);
  assertTrue(n.estimated(9*S+20_000_000L));assertFalse(n.followingRoute());
  assertTrue("Missing sensor seconds must not become a route jump",Math.abs(n.latitude()-before)*111320<1);
 }
 @Test public void movingGnssAndCrossTrackResidualFinishRecoveryWithImuStillActive(){
  for(boolean routed:new boolean[]{false,true}) {
   LiveNavigation n=prepare(routed);n.providerDisabled();
   for(int i=1;i<=500;i++)sample(n,4*S+i*10_000_000L);
   double targetNorth=(n.latitude()-23.3)*111320+40;
   double targetLon=77.35+3/(111320*Math.cos(Math.toRadians(23.3)));
   for(int i=0;i<=2000;i++) {
    long t=9*S+i*10_000_000L;sample(n,t);
    if(i%100==0)n.fix(new NavigationFusion.Fix(t,23.3+(targetNorth+i*.1)/111320,targetLon,0,3,10,.2,0,5),t);
    n.tick(t);
   }
   assertTrue(n.healthy(29*S));assertTrue(n.fused(29*S));
   assertFalse("Normal GPS/filter residual must not keep recovery active",n.status(29*S).contains("Reconciling"));
   double before=n.latitude();sample(n,29*S+100_000_000L);n.tick(29*S+100_000_000L);
   assertTrue("Healthy tracking moves between fixes after recovery",n.latitude()>before);
  }
 }
}
