package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
public class PredictionFreshnessTest {
 @Test public void stoppedSensorStreamCannotKeepAiWindowReady(){
  var f=new FeatureBuffer();for(int i=0;i<300;i++)f.add(new ImuSample(1_000_000_000L+i*10_000_000L,0,0,9.80665,0,0,0));
  assertTrue(f.ready(4_000_000_000L));assertFalse(f.ready(5_000_000_000L));
  assertFalse(f.ready(3_000_000_000L));
 }
 @Test public void rawExperimentalSpeedIsVisibleButInvalidAndStaleReadingsAreNot(){
  var r=new AiSpeedReading();assertFalse(r.available(1_000_000_000L));
  r.update(1_000_000_000L,7.5);assertTrue(r.available(2_000_000_000L));assertEquals(7.5,r.speed(2_000_000_000L),0);
  assertFalse(r.available(4_000_000_000L));r.update(4_000_000_000L,Double.NaN);assertFalse(r.available(4_000_000_000L));
  r.update(5_000_000_000L,-1);assertFalse(r.available(5_000_000_000L));
  r.update(6_000_000_000L,81);assertFalse(r.available(6_000_000_000L));
  r.update(7_000_000_000L,0);assertTrue(r.available(7_000_000_000L));assertEquals(0,r.speed(7_000_000_000L),0);
 }
}
