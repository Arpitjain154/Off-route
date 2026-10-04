package com.idr.navigator;
import static org.junit.Assert.*;
import android.Manifest;
import android.content.*;
import android.location.Location;
import android.os.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.idr.navigator.engine.SensorCaptureService;
import java.util.concurrent.*;
import org.junit.Test;

/** Controlled GPS inputs on an emulator; never reported as a physical trip. */
public class NavigationBehaviorTest {
    private volatile double lat=23.2584857,lon=77.401989,speed=10,bearing=0;
    private volatile boolean paused;
    @Test public void movingMarkerReroutingHeadingAndGpsLoss() throws Exception {
        org.junit.Assume.assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));
        var instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        for(String permission:new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION})
            instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(),permission);
        ScheduledExecutorService feed=Executors.newSingleThreadScheduledExecutor();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)) {
            long deadline=SystemClock.elapsedRealtime()+60000;
            while((SensorCaptureService.instance==null||!SensorCaptureService.instance.isReady())&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
            SensorCaptureService engine=SensorCaptureService.instance;assertNotNull(engine);
            // This scenario supplies GPS only; emulator hardware locations must
            // not compete with its trajectory. Sensor fallback has its own soak.
            CountDownLatch isolated=new CountDownLatch(1);
            engine.handler.post(()->{try {
                var locations=SensorCaptureService.class.getDeclaredField("locations");locations.setAccessible(true);
                ((android.location.LocationManager)locations.get(engine)).removeUpdates(engine);
                var sensors=SensorCaptureService.class.getDeclaredField("sensors");sensors.setAccessible(true);
                ((android.hardware.SensorManager)sensors.get(engine)).unregisterListener(engine);
            }catch(Exception error){throw new RuntimeException(error);}finally{isolated.countDown();}});
            assertTrue(isolated.await(10,TimeUnit.SECONDS));
            feed.scheduleAtFixedRate(()->{if(paused)return;engine.handler.post(()->{
                Location fix=new Location("gps");fix.setLatitude(lat);fix.setLongitude(lon);fix.setAccuracy(3);fix.setSpeed((float)speed);
                fix.setSpeedAccuracyMetersPerSecond(.2f);fix.setBearing((float)bearing);fix.setBearingAccuracyDegrees(5);
                fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());fix.setTime(System.currentTimeMillis());engine.onLocationChanged(fix);
            });},0,500,TimeUnit.MILLISECONDS);
            js(activity,"document.querySelector('.mobile-nav button[aria-label=\"Navigate\"]')?.click()");
            until(activity,"!!document.querySelector('.destination-launcher')",30000);
            SystemClock.sleep(1500);
            js(activity,"(()=>{window.__routeCalls=0;const original=Capacitor.nativePromise;Capacitor.nativePromise=function(p,m,o){if(p==='Directions')window.__routeCalls++;return original.apply(this,arguments);};document.querySelector('.destination-launcher').click();})()");
            until(activity,"document.querySelectorAll('.destination-result').length>0",30000);
            js(activity,"(()=>{const e=document.querySelectorAll('.destination-field input')[1];Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,'Indore');e.dispatchEvent(new Event('input',{bubbles:true}));})()");
            until(activity,"[...document.querySelectorAll('.destination-result strong')].some(e=>e.textContent==='Indore')",10000);
            js(activity,"[...document.querySelectorAll('.destination-result')].find(e=>e.querySelector('strong').textContent==='Indore').click()");
            js(activity,"document.querySelector('.destination-footer .primary').click()");
            until(activity,"!document.querySelector('dialog')",60000);
            until(activity,"document.querySelectorAll('.destination-pin').length===1",10000);
            assertEquals("No breadcrumb trail during navigation","false",js(activity,"!!document.querySelector('[aria-label=\"Route starting point\"]')"));
            double startLon=Double.parseDouble(js(activity,"Number(document.querySelector('.position-halo').dataset.longitude)"));
            bearing=90;
            for(int step=0;step<24;step++){lon+=.0001;SystemClock.sleep(600);}
            double movedLon=Double.parseDouble(js(activity,"Number(document.querySelector('.position-halo').dataset.longitude)"));
            assertTrue("Live marker did not move",movedLon>startLon+.0015);
            assertEquals("Eastward GPS course: "+engine.position(),90,engine.position().getDouble("heading"),10);
            assertTrue(engine.position().getDouble("speed")>9);
            until(activity,"window.__routeCalls>=2",45000);
            bearing=180;SystemClock.sleep(2000);assertTrue(Math.abs(engine.position().getDouble("heading")-180)<10);
            assertTrue("Turn falsely stopped vehicle",engine.position().getDouble("speed")>9);
            paused=true;SystemClock.sleep(700);engine.handler.post(()->engine.onProviderDisabled("gps"));
            SystemClock.sleep(1000);
            if(engine.position().optBoolean("estimated")) {
                assertTrue(engine.position().optBoolean("speedAvailable"));
                until(activity,"!!document.querySelector('[aria-label=\"Sensor-estimated position\"]')",10000);
            } else {
                // This GPS-only test does not manufacture mounting alignment.
                until(activity,"document.querySelector('.hud-speed>span').textContent==='—'",10000);
                assertFalse(engine.position().optBoolean("speedAvailable",true));
            }
            speed=15;paused=false;SystemClock.sleep(4000);assertTrue(engine.position().getDouble("speed")>14);
            assertEquals(1,engine.position().getInteger("locationRegistrations").intValue());
            assertSame(engine,SensorCaptureService.instance);
            android.util.Log.i("IDR-Navigation-Test","PASS live marker, real offline rerouting, 90/180 degree headings, constant turn speed and GPS recovery; controlled emulator inputs");
        }finally{feed.shutdownNow();context.stopService(new Intent(context,SensorCaptureService.class));}
    }
    private static String js(ActivityScenario<MainActivity> activity,String expression) throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] value={null};
        activity.onActivity(a->a.getBridge().getWebView().evaluateJavascript(expression,r->{value[0]=r;done.countDown();}));
        assertTrue("WebView did not respond: "+expression,done.await(30,TimeUnit.SECONDS));return value[0];
    }
    private static void until(ActivityScenario<MainActivity> activity,String condition,long timeout) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<deadline){if("true".equals(js(activity,condition)))return;SystemClock.sleep(200);}
        fail("Timed out: "+condition+"; page="+js(activity,"document.body.innerText"));
    }
}
