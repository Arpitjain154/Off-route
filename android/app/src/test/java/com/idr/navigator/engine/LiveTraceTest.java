package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
public class LiveTraceTest {
 @Test public void traceSeparatesDisplayedEngineFromResearchFilter() throws Exception {
  var file=Files.createTempFile("live-trace", ".csv");
  try {
   var research=new NavigationFusion();research.filter.initialize(23.3,77.35,0);
   var live=new LiveNavigation();long t=1_000_000_000L;
   live.gravity(t,0,0,9.80665);live.compass(t,0,5);live.fix(new NavigationFusion.Fix(t,23.3,77.35,0,3,10,.2,0,5),t);
   live.imu(new ImuSample(t,0,0,9.80665,0,0,0));
   try(var trace=new FusionTraceWriter(file.toFile())) {trace.write(t,research,live);}
   var lines=Files.readAllLines(file);var names=Arrays.asList(lines.get(0).split(","));var values=lines.get(1).split(",");
   assertEquals(names.size(),values.length);assertEquals("false",values[names.indexOf("heading_ready")]);
   assertTrue("Record live alignment separately",names.contains("nav_aligned"));
   assertEquals("true",values[names.indexOf("nav_aligned")]);
   assertEquals(9.80665,Double.parseDouble(values[names.indexOf("ins_gravity_z")]),.00001);
   assertEquals(3,Double.parseDouble(values[names.indexOf("nav_uncertainty_m")]),.001);
  } finally {Files.deleteIfExists(file);}
 }
}
