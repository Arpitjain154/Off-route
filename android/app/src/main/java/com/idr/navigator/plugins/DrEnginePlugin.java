package com.idr.navigator.plugins;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
import com.idr.navigator.engine.SensorCaptureService;

@CapacitorPlugin(
    name = "DrEngine",
    permissions = {
        @Permission(
            alias = "location",
            strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            }
        ),
        @Permission(alias = "activity", strings = { Manifest.permission.ACTIVITY_RECOGNITION }),
        @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS }),
    }
)
public final class DrEnginePlugin extends Plugin {

    @PluginMethod public void setAppearance(PluginCall call) {
        boolean dark=Boolean.TRUE.equals(call.getBoolean("dark",false));
        getActivity().runOnUiThread(()->{
            var window=getActivity().getWindow();
            var controller=androidx.core.view.WindowCompat.getInsetsController(window,window.getDecorView());
            controller.setAppearanceLightStatusBars(!dark);
            controller.setAppearanceLightNavigationBars(!dark);
            call.resolve();
        });
    }

    @PluginMethod public void reportDiagnostic(PluginCall call) {
        String message = call.getString("message", "Unknown map error");
        if (message.length() > 8192) message = message.substring(0, 8192);
        com.idr.navigator.engine.EngineDiagnostics.failure(getContext(), "Web navigation error", new IllegalStateException(message));
        call.resolve();
    }

    @PluginMethod
    public void startEngine(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "startAfterPermission");
            return;
        }
        launch(call);
    }

    @PermissionCallback
    private void startAfterPermission(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) launch(call);
        else call.reject("Precise location is required to initialize tracking.");
    }

    private void launch(PluginCall call) {
        getActivity().runOnUiThread(() -> launchVisible(call));
    }

    private void launchVisible(PluginCall call) {
        try {
            if (!getActivity().getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                call.reject("Keep the app open while starting the engine, then try again.");
                return;
            }
            SensorCaptureService existing = SensorCaptureService.instance;
            if (existing != null && existing.isReady()) { call.resolve(); return; }
            SensorCaptureService.startupError = null;
            getContext().startForegroundService(
                new Intent(getContext(), SensorCaptureService.class)
            );
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            long deadline = android.os.SystemClock.elapsedRealtime() + 20_000;
            main.post(new Runnable() {
                public void run() {
                    String failure = SensorCaptureService.startupError;
                    SensorCaptureService service = SensorCaptureService.instance;
                    if (failure != null) { call.reject(failure); return; }
                    if (service != null && service.isReady()) { call.resolve(); return; }
                    if (android.os.SystemClock.elapsedRealtime() >= deadline) {
                        getContext().stopService(new Intent(getContext(), SensorCaptureService.class));
                        call.reject("The engine could not finish starting. Export engine diagnostics from Settings.");
                        return;
                    }
                    main.postDelayed(this, 100);
                }
            });
        } catch (Exception | LinkageError e) {
            com.idr.navigator.engine.EngineDiagnostics.failure(getContext(), "Requesting engine start", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void stopEngine(PluginCall call) {
        getContext().stopService(new Intent(getContext(), SensorCaptureService.class));
        call.resolve();
    }

    @PluginMethod
    public void getPosition(PluginCall call) {
        SensorCaptureService service = SensorCaptureService.instance;
        if (service == null || !service.isReady()) {
            call.reject(SensorCaptureService.startupError == null ? "Tracking engine is not running" : SensorCaptureService.startupError);
            return;
        }
        JSObject pose = service.position();
        if (!pose.has("lat")) {
            pose = new JSObject();
            pose.put("lat", 23.2418);
            pose.put("lon", 77.4029);
            pose.put("ready", false);
            pose.put("heading", 0);
            pose.put("speed", 0);
            pose.put("accuracy", 0);
            pose.put("confidence", 0);
            pose.put("health", "BLACKOUT");
            pose.put("source", "device");
            for (String key : new String[] {
                "distance",
                "elapsed",
                "bytes",
                "satellites",
                "cn0",
                "gpsTrust",
                "physicsTrust",
                "mapTrust",
                "aiTrust",
            })
                pose.put(key, 0);
        }
        call.resolve(pose);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        java.util.List<String> aliases = new java.util.ArrayList<>();
        aliases.add("location");
        if (Build.VERSION.SDK_INT >= 29) aliases.add("activity");
        if (Build.VERSION.SDK_INT >= 33) aliases.add("notifications");
        requestPermissionForAliases(aliases.toArray(new String[0]), call, "permissionsResult");
    }

    @PermissionCallback
    private void permissionsResult(PluginCall call) {
        JSObject result = new JSObject();
        result.put("location", getPermissionState("location").toString());
        call.resolve(result);
    }

    @PluginMethod
    public void backgroundSettings(PluginCall call) {
        Intent intent = new Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + getContext().getPackageName())
        );
        getActivity().startActivity(intent);
        call.resolve();
    }
}
