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
}
