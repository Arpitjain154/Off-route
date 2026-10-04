package com.idr.navigator;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.idr.navigator.engine.SensorCaptureService;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real Android service/runtime regression; no sensor service or native model mocks. */
@RunWith(AndroidJUnit4.class)
public class EngineStartupTest {
    private static volatile byte[] otherAppMemory;

    @Test public void diagnosticsCanBeExportedWithoutARecordedDrive() throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        com.idr.navigator.engine.EngineDiagnostics.export(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), output);
        String report = output.toString("UTF-8");
        assertTrue(report.contains("IDR engine diagnostic report"));
        assertTrue(report.contains("Android: "));
        assertTrue(report.contains("App: "));
    }

    @Test public void statewideGraphLoadsWithOtherAppMemoryInUse() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        for (String permission : new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION})
            instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), permission);
        Intent service = new Intent(context, SensorCaptureService.class);
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            // Model a busy WebView/map UI. The statewide graph must not require another ~100 MB Java heap.
            otherAppMemory = new byte[96 * 1024 * 1024];
            activity.onActivity(a -> a.startForegroundService(service));
            long deadline = SystemClock.elapsedRealtime() + 45_000;
            String status = "";
            while (SystemClock.elapsedRealtime() < deadline) {
                SensorCaptureService serviceInstance = SensorCaptureService.instance;
                if (serviceInstance != null) status = serviceInstance.position().optString("mapStatus");
                if (status.contains("ready") || status.contains("unavailable")) break;
                SystemClock.sleep(100);
            }
            assertTrue("Offline graph did not load: " + status, status.contains("HMM ready"));
            assertNotNull(otherAppMemory);
        } finally {
            context.stopService(service);
            otherAppMemory = null;
        }
    }

    @Test public void startStopAndRestartKeepsAppAlive() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        for (String permission : new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION}) {
            instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), permission);
        }
        Intent service = new Intent(context, SensorCaptureService.class);
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            for (int attempt = 0; attempt < 3; attempt++) {
                activity.onActivity(a -> a.startForegroundService(service));
                long deadline = SystemClock.elapsedRealtime() + 30_000;
                while ((SensorCaptureService.instance == null ||
                        !SensorCaptureService.instance.position().has("lat")) &&
                        SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
                assertNotNull("Service did not start", SensorCaptureService.instance);
                assertTrue("No engine snapshot", SensorCaptureService.instance.position().has("lat"));
                // Exercise the first model inference and live callbacks, not just service creation.
                SystemClock.sleep(5_000);
                assertNotNull("Engine stopped after startup", SensorCaptureService.instance);
                assertNotNull("Bundled native models failed to load", SensorCaptureService.instance.models);
                context.stopService(service);
                deadline = SystemClock.elapsedRealtime() + 30_000;
                while (SensorCaptureService.instance != null && SystemClock.elapsedRealtime() < deadline)
                    SystemClock.sleep(100);
                assertNull("Service did not stop", SensorCaptureService.instance);
            }
        } finally { context.stopService(service); }
    }
}
