package com.idr.navigator.plugins;

import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.idr.navigator.cloud.*;
import java.io.*;
import org.json.*;

@CapacitorPlugin(name = "CloudPlugin")
public final class CloudPlugin extends Plugin {

    interface Action {
        void run() throws Exception;
    }

    private void run(PluginCall call, Action action) {
        getBridge().execute(() -> {
            try {
                action.run();
            } catch (Exception e) {
                call.reject(e.getMessage() == null ? "Cloud operation failed" : e.getMessage());
            }
        });
    }

    private CloudSessionStore store() {
        return new CloudSessionStore(getContext());
    }

    @PluginMethod
    public void configure(PluginCall call) {
        run(call, () -> {
            CloudScheduler.cancel(getContext());
            store().configure(
                call.getString("url", ""),
                call.getString("key", ""),
                call.getString("bucket", "journeys")
            );
            call.resolve();
        });
    }

    @PluginMethod
    public void signIn(PluginCall call) {
        auth(call, false);
    }

    @PluginMethod
    public void signUp(PluginCall call) {
        auth(call, true);
    }

    private void auth(PluginCall call, boolean signup) {
        run(call, () -> {
            CloudScheduler.cancel(getContext());
            String message = store().signIn(
                call.getString("email", ""),
                call.getString("password", ""),
                signup
            );
            CloudScheduler.schedule(getContext());
            call.resolve(new JSObject().put("message", message));
        });
    }

    @PluginMethod
    public void signOut(PluginCall call) {
        run(call, () -> {
            store().signOut();
            CloudScheduler.cancel(getContext());
            call.resolve();
        });
    }

    @PluginMethod
    public void setPreferences(PluginCall call) {
        run(call, () -> {
            CloudScheduler.preferences(
                getContext(),
                Boolean.TRUE.equals(call.getBoolean("consent", false)),
                Boolean.TRUE.equals(call.getBoolean("wifiOnly", true))
            );
            call.resolve();
        });
    }

    @PluginMethod
    public void enqueueTrip(PluginCall call) {
        run(call, () -> {
            String id = call.getString("sessionId", "");
            TripPackage.identity(id);
            if (
                !new File(getContext().getFilesDir(), "drives/" + id + ".summary.json").isFile()
            ) throw new IOException(
                "Finish this recording before upload; interrupted recordings need recovery"
            );
            CloudScheduler.enqueue(getContext(), id);
            call.resolve();
        });
    }

    @PluginMethod
    public void retryTrip(PluginCall call) {
        enqueueTrip(call);
    }

    @PluginMethod
    public void checkModelUpdates(PluginCall call) {
        run(call, () -> {
            CloudScheduler.modelCheck(getContext());
            call.resolve();
        });
    }

    @PluginMethod
    public void applyPendingModels(PluginCall call) {
        run(call, () -> {
            long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
            while (
                    com.idr.navigator.engine.SensorCaptureService.instance != null &&
                    android.os.SystemClock.elapsedRealtime() < deadline
                )
                Thread.sleep(100);
            synchronized (com.idr.navigator.engine.ModelInstaller.LOCK) {
                if (
                    com.idr.navigator.engine.SensorCaptureService.instance != null
                ) throw new IOException("Tracking is still active");
                CloudSessionStore s = store();
                String user = s.user();
                if (user.isEmpty()) throw new IOException("Sign in first");
                int installed = 0;
                File[] dirs = new File(
                    getContext().getFilesDir(),
                    "cloud-pending-models"
                ).listFiles();
                if (dirs != null) for (File dir : dirs) {
                    File manifest = new File(dir, "release.json");
                    if (!manifest.isFile()) continue;
                    JSONObject pending = TripPackage.json(manifest);
                    if (
                        !user.equals(pending.optString("userId")) ||
                        !s.config().projectUrl.equals(pending.optString("project"))
                    ) continue;
                    JSONObject j = pending.getJSONObject("release");
                    String name = j.getString("name");
                    ModelRelease r = ModelRelease.parse(
                        j,
                        com.idr.navigator.engine.ModelInstaller.current(getContext(), name),
                        getContext()
                            .getPackageManager()
                            .getPackageInfo(getContext().getPackageName(), 0)
                            .versionCode
                    );
                    com.idr.navigator.engine.ModelInstaller.install(
                        getContext(),
                        name,
                        r.version,
                        r.sha256,
                        new File(dir, "candidate.tflite"),
                        false,
                        true
                    );
                    installed++;
                    manifest.delete();
                    new File(dir, "candidate.tflite").delete();
                    dir.delete();
                }
                s.updateMessage(
                    installed > 0
                        ? "Approved models updated; previous versions retained"
                        : "No pending model update"
                );
                call.resolve();
            }
        });
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        run(call, () -> {
            CloudSessionStore s = store();
            CloudConfig cfg = s.config();
            JSONObject session = s.session();
            String user = s.user();
            JSObject result = new JSObject();
            result.put("connected", session != null);
            result.put(
                "email",
                session == null ? "" : session.getJSONObject("user").optString("email")
            );
            result.put("url", cfg.projectUrl);
            result.put("keyConfigured", !cfg.publishableKey.isEmpty());
            result.put("bucket", cfg.journeyBucket);
            result.put("consent", s.consent());
            result.put("wifiOnly", s.wifiOnly());
            result.put("modelMessage", s.updateMessage());
            JSONArray queue = CloudScheduler.queue(getContext()).list(cfg.projectUrl, user),
                safe = new JSONArray();
            for (int i = 0; i < queue.length(); i++) {
                JSONObject j = queue.getJSONObject(i);
                safe.put(
                    new JSONObject()
                        .put("sessionId", j.getString("sessionId"))
                        .put("state", j.optString("state"))
                        .put("message", j.optString("message"))
                );
            }
            result.put("queue", safe);
            call.resolve(result);
        });
    }
}
