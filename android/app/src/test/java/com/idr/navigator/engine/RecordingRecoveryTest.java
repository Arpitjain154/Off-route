package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.UUID;
public class RecordingRecoveryTest {
 @Test public void completeSamplesSurvivePartialTailAndIdentityIsChecked()throws Exception {
  var dir=Files.createTempDirectory("idr-recovery");String id=UUID.randomUUID().toString();var file=dir.resolve(id+".drlog").toFile();
  try(var log=new DrLogWriter(file,100,"Test phone","dashboard",id)){for(int i=0;i<10;i++)log.write(1_000_000_000L+i*10_000_000L,new float[]{0,0,9.8f},new float[3],new float[3],23,77,500,10,3,10,35,3);}
  Files.write(file.toPath(),new byte[]{1,2,3},StandardOpenOption.APPEND);
  var r=LogRecovery.read(file,id);
  var upload=LogRecovery.uploadable(file,id);assertEquals(0,(upload.length()-200)%70);assertEquals(903,file.length());assertEquals(900,upload.length());assertEquals(.09,LogRecovery.read(upload,id).durationSeconds(),1e-8);assertEquals(.09,r.durationSeconds(),1e-8);assertEquals(.9,r.distanceMeters(),1e-8);assertEquals("Test phone",r.phone());
  try{LogRecovery.read(file,UUID.randomUUID().toString());fail("wrong identity accepted");}catch(java.io.IOException expected){}
 }
 @Test public void qualityMetadataNeverOverwritesEarlierSession()throws Exception {
  var dir=Files.createTempDirectory("idr-quality");String id=UUID.randomUUID().toString();
  try(var w=new TrainingQualityWriter(dir.toFile(),id)){w.write(1_000_000_001L,1_000_000_000L,.3,true);}
  String text=new String(Files.readAllBytes(dir.resolve(id+".quality.csv")),java.nio.charset.StandardCharsets.UTF_8);assertTrue(text.contains("1000000001,1000000000,0.3,1"));
  try{new TrainingQualityWriter(dir.toFile(),id);fail("overwritten");}catch(java.io.IOException expected){}
  assertEquals(text,new String(Files.readAllBytes(dir.resolve(id+".quality.csv")),java.nio.charset.StandardCharsets.UTF_8));
 }
 @Test public void headerIsRecoverableBeforeFirstSample()throws Exception {
  var dir=Files.createTempDirectory("idr-header");String id=UUID.randomUUID().toString();var file=dir.resolve(id+".drlog").toFile();
  try(var log=new DrLogWriter(file,100,"Test phone","dashboard",id)){assertEquals(200,file.length());assertEquals(0,LogRecovery.read(file,id).durationSeconds(),0);}
 }
}
