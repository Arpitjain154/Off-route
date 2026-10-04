package com.idr.navigator;

import static org.junit.Assert.*;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.idr.navigator.engine.SensorCaptureService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GpsLockTest {
    @Test public void roadEncodingWorksWithoutJavaCompilerClasses() {
        assertEquals("road_access", new SimpleBooleanEncodedValue("road_access").getName());
        try { new SimpleBooleanEncodedValue("class"); fail("Reserved Java keyword accepted"); }
        catch (IllegalArgumentException expected) {}
    }

    @Test public void tabReturnsAndActivityRecreationKeepOneGpsWatch() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        for (String permission : new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
            instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), permission);
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            long deadline = SystemClock.elapsedRealtime() + 30_000;
            while ((SensorCaptureService.instance == null || !SensorCaptureService.instance.isReady()) &&
                SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
            SensorCaptureService original = SensorCaptureService.instance;
            assertNotNull("App launch did not start GPS", original);
            for (int cycle = 0; cycle < 3; cycle++) {
                for (String tab : new String[]{"My drives", "Settings", "Navigate"}) {
                    js(activity, "document.querySelector('.mobile-nav button[aria-label=\"" + tab + "\"]')?.click()");
                    deadline = SystemClock.elapsedRealtime() + 5_000;
                    String selected;
                    do {
                        SystemClock.sleep(100);
                        selected = js(activity, "document.querySelector('.mobile-nav button.active')?.getAttribute('aria-label')");
                    } while (!selected.equals("\"" + tab + "\"") && SystemClock.elapsedRealtime() < deadline);
                    assertEquals("Tab navigation did not complete", "\"" + tab + "\"", selected);
                    assertSame(original, SensorCaptureService.instance);
                    assertEquals(1, original.position().getInteger("locationRegistrations").intValue());
                }
                activity.recreate();
                SystemClock.sleep(2_000);
                assertEquals("\"alive\"", js(activity, "'alive'"));
                assertSame("Activity recreation duplicated the service", original, SensorCaptureService.instance);
                assertEquals(1, original.position().getInteger("locationRegistrations").intValue());
            }
        } finally { context.stopService(new Intent(context, SensorCaptureService.class)); }
    }

    /** Ten minutes of wall-clock Android service + WebView tracking. Locations are injected test data. */
    @Test public void autoStartGpsLockHeadingAndLifecycleSoak() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        org.junit.Assume.assumeTrue("Opt in with -e idrSoak true", "true".equals(
            InstrumentationRegistry.getArguments().getString("idrSoak")));
        Context context = instrumentation.getTargetContext();
        for (String permission : new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION})
            instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), permission);
        context.stopService(new Intent(context, SensorCaptureService.class));
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        while (SensorCaptureService.instance != null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            deadline = SystemClock.elapsedRealtime() + 30_000;
            while ((SensorCaptureService.instance == null || !SensorCaptureService.instance.isReady()) &&
                SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
            SensorCaptureService original = SensorCaptureService.instance;
            assertNotNull("Opening the app did not auto-start GPS", original);
            assertTrue(original.isReady());
            assertFalse(js(activity, "document.body.innerText.includes('Start engine')").equals("true"));
            assertFalse(original.position().optBoolean("ready", false));
            long started = SystemClock.elapsedRealtime();
            int samples = 0;
            boolean sawHeadingPending = false, sawTracking = false, sawMatching = false;
            while (SystemClock.elapsedRealtime() - started < 600_000) {
                double seconds = (SystemClock.elapsedRealtime() - started) / 1000.0;
                Location fix = new Location("gps");
                fix.setLatitude(23.2418 + Math.min(seconds, 100) * 0.00003);
                fix.setLongitude(77.4029);
                fix.setAccuracy(3); fix.setAltitude(500);
                fix.setTime(System.currentTimeMillis()); fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                if (seconds > 10) {
                    fix.setSpeed(seconds < 100 ? 4 : 0); fix.setSpeedAccuracyMetersPerSecond(.2f);
                    fix.setBearing(0); fix.setBearingAccuracyDegrees(5);
                }
                CountDownLatch delivered = new CountDownLatch(1);
                original.handler.post(() -> { original.onLocationChanged(fix); delivered.countDown(); });
                assertTrue("Sensor handler stalled", delivered.await(5, TimeUnit.SECONDS));
                SystemClock.sleep(1000);
                assertSame("Lifecycle created another service", original, SensorCaptureService.instance);
                assertTrue("Engine failed: " + SensorCaptureService.startupError, original.isReady());
                var pose = original.position();
                assertEquals(1, pose.getInteger("locationRegistrations").intValue());
                assertTrue(pose.optBoolean("ready", false));
                assertTrue(Double.isFinite(pose.getDouble("heading")));
                if (seconds < 10) sawHeadingPending |= !pose.optBoolean("headingReady", true);
                if (seconds > 10) sawTracking |= pose.optBoolean("headingReady", false);
                String mapStatus = pose.getString("mapStatus", "");
                assertFalse(mapStatus, mapStatus.contains("unavailable"));
                sawMatching |= mapStatus.contains("active") || mapStatus.contains("consistent road");
                if (++samples % 30 == 0) {
                    // Trigger tab mounts and the actual visibility listener while location updates continue.
                    js(activity, "Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='Settings')?.click()");
                    js(activity, "Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='Navigate')?.click()");
                    activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
                    SystemClock.sleep(250);
                    activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
                    assertEquals("Native bridge/renderer stopped", "\"alive\"", js(activity, "'alive'"));
                    android.util.Log.i("IDR-Soak", "seconds=" + seconds + " generation=" + pose.getInteger("engineGeneration") +
                        " listeners=" + pose.getInteger("locationRegistrations") + " headingReady=" + pose.optBoolean("headingReady", false) +
                        " map=" + mapStatus);
                }
            }
            assertTrue("Did not observe GPS lock with missing heading", sawHeadingPending);
            assertTrue("Did not establish heading", sawTracking);
            assertTrue("Road matching path was not exercised", sawMatching);
            assertEquals("\"alive\"", js(activity, "'alive'"));
        } finally { context.stopService(new Intent(context, SensorCaptureService.class)); }
    }

    private static String js(ActivityScenario<MainActivity> activity, String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        String[] result = new String[1];
        activity.onActivity(a -> a.getBridge().getWebView().evaluateJavascript(script, value -> { result[0] = value; done.countDown(); }));
        assertTrue("WebView did not respond", done.await(5, TimeUnit.SECONDS));
        return result[0];
    }
}
