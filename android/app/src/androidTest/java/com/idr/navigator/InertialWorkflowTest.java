package com.idr.navigator;

import static org.junit.Assert.*;
import android.Manifest;
import android.content.*;
import android.hardware.SensorManager;
import android.location.*;
import android.os.*;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.idr.navigator.engine.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** Six-minute controlled-input integration soak. Not a physical accuracy benchmark. */
public class InertialWorkflowTest {
    private static Object field(Object object,String name) throws Exception {
        var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    @Test public void repeatedOutagesAccelerationTurnsRecoveryAndLifecycle() throws Exception {
        org.junit.Assume.assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));
        var instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        for(String permission:new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION})
            instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(),permission);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)) {
            long deadline=SystemClock.elapsedRealtime()+60000;
            while((SensorCaptureService.instance==null||!SensorCaptureService.instance.isReady())&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
            SensorCaptureService engine=SensorCaptureService.instance;assertNotNull("Auto-connect did not start",engine);assertTrue(engine.isReady());
            LiveNavigation live=(LiveNavigation)field(engine,"live");
            CountDownLatch configured=new CountDownLatch(1);
            engine.handler.post(()->{try{
                ((SensorManager)field(engine,"sensors")).unregisterListener(engine);
                ((LocationManager)field(engine,"locations")).removeUpdates(engine);
            }catch(Throwable error){failure.set(error);}finally{configured.countDown();}});
            assertTrue(configured.await(10,TimeUnit.SECONDS));assertNull(failure.get());
            long start=SystemClock.elapsedRealtimeNanos();
            Runnable feed=new Runnable(){long prior=start,lastGps,phaseIndex=-1;double latitude=23.2584857,longitude=77.401989,heading;
                public void run(){try{
                    long now=SystemClock.elapsedRealtimeNanos();double elapsed=(now-start)*1e-9,phase=elapsed%120,dt=(now-prior)*1e-9;prior=now;
                    if(elapsed>=362)return;
                    double velocity=phase<15?8:phase<20?8+(phase-15):phase<35?13:phase<40?13-(phase-35):phase<70?8:phase<78?78-phase:0;
                    double acceleration=phase>=15&&phase<20?1:phase>=35&&phase<40?-1:phase>=70&&phase<78?-1:0;
                    double yaw=phase>=25&&phase<30?Math.PI/10:phase>=55&&phase<65?Math.PI/10:0;
                    heading=(heading+Math.toDegrees(yaw)*dt)%360;
                    latitude+=velocity*Math.cos(Math.toRadians(heading))*dt/111320;
                    longitude+=velocity*Math.sin(Math.toRadians(heading))*dt/(111320*Math.cos(Math.toRadians(latitude)));
                    live.gravity(now,0,0,9.80665);live.compass(now,heading,5);
                    boolean outage=phase>=10&&phase<50;
                    long index=(long)(elapsed/120);
                    if(outage&&phaseIndex!=index){engine.onProviderDisabled("gps");phaseIndex=index;}
                    if((!outage||phase>=45)&&now-lastGps>=1_000_000_000L){
                        Location fix=new Location("gps");fix.setLatitude(latitude);fix.setLongitude(longitude);fix.setAccuracy(outage?40:3);
                        fix.setSpeed((float)velocity);fix.setSpeedAccuracyMetersPerSecond(.2f);fix.setBearing((float)heading);fix.setBearingAccuracyDegrees(5);
                        fix.setElapsedRealtimeNanos(now);fix.setTime(System.currentTimeMillis());engine.onLocationChanged(fix);lastGps=now;
                    }
                    engine.acceptImu(new ImuSample(now,-velocity*yaw,acceleration,9.80665,0,0,-yaw),true);
                    if(phase>12&&phase<49){assertTrue("Outage position unavailable",live.estimated(now));assertTrue("Blank outage speed",live.speedAvailable(now));
                        assertEquals("Inertial acceleration/braking",velocity,live.speed(now),1.2);}
                    if(phase>30&&phase<34)assertTrue("Turn lost speed",live.speed(now)>11);
                    if(phase>90)assertEquals("Stationary speed",0,live.speed(now),.01);
                    if(phase>52&&phase<68)assertFalse("Did not recover GNSS",live.estimated(now));
                    assertTrue(Double.isFinite(live.latitude()+live.longitude()+live.heading()+live.speed(now)));
                    engine.handler.postDelayed(this,10);
                }catch(Throwable error){failure.compareAndSet(null,error);}}
            };
            engine.handler.post(feed);
            try(java.io.PrintWriter trace=new java.io.PrintWriter(new java.io.File(context.getFilesDir(),"inertial-workflow.csv"))){
                trace.println("elapsed_s,lat,lon,speed,heading,estimated,source");
                for(int second=0;second<360;second++){
                    SystemClock.sleep(1000);if(failure.get()!=null)throw new AssertionError("Sensor workflow",failure.get());
                    assertSame("Engine restarted unexpectedly",engine,SensorCaptureService.instance);assertTrue(engine.isReady());
                    var p=engine.position();trace.println(second+","+p.optDouble("lat")+","+p.optDouble("lon")+","+p.optDouble("speed")+","+p.optDouble("heading")+","+p.optBoolean("estimated")+","+p.optString("speedSource"));
                    if(second==60||second==180||second==300){activity.moveToState(Lifecycle.State.CREATED);SystemClock.sleep(500);activity.moveToState(Lifecycle.State.RESUMED);}
                    if(second%30==0)android.util.Log.i("IDR-INS-QA","Controlled soak elapsed="+second+" source="+p.optString("speedSource"));
                }
            }finally{engine.handler.removeCallbacks(feed);}
            assertEquals(1,engine.position().getInteger("locationRegistrations").intValue());
            assertNotNull("Actual bundled TFLite runtime",engine.models);
            assertTrue("Motion and speed models never received a complete sensor window",engine.position().optLong("modelInferences")>30);
            android.util.Log.i("IDR-INS-QA","PASS six-minute controlled soak: 3 outages, accel/braking, turns, degraded fixes, recovery, stops, lifecycle; physical drift not measured");
        }finally{context.stopService(new Intent(context,SensorCaptureService.class));}
    }
}
