package com.idr.navigator.cloud;

import android.content.Context;
import androidx.work.*;
import com.idr.navigator.engine.*;
import java.io.*;
import org.json.*;

public final class ModelUpdateWorker extends Worker {

    public ModelUpdateWorker(Context c, WorkerParameters p) {
        super(c, p);
    }

    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        CloudSessionStore s = new CloudSessionStore(c);
        try {
            String user = s.user();
            if (user.isEmpty()) return Result.success();
            long generation = s.generation();
            CloudConfig cfg = s.config();
            SupabaseClient.Guard guard = () -> {
                if (isStopped()) throw new IOException("Model check paused");
                s.guard(user, generation, false);
            };
            File dir = new File(c.getCacheDir(), "cloud-models/" + getId());
            dir.mkdirs();
            File index = new File(dir, "approved.json");
            try {
                SupabaseClient.transfer(
                    s,
                    cfg.modelBucket,
                    "approved.json",
                    index,
                    false,
                    guard,
                    1024 * 1024
                );
            } catch (SupabaseClient.HttpError e) {
                if (e.status == 404) {
                    s.updateMessage("No approved model release available");
                    return Result.success();
                }
                throw e;
            }
            JSONArray models = TripPackage.json(index).getJSONArray("models");
            boolean changed = false;
            for (int i = 0; i < models.length(); i++) {
                JSONObject j = models.getJSONObject(i);
                String name = j.getString("name");
                if (
                    !java.util.Arrays.asList(TfliteModels.NAMES).contains(name)
                ) throw new IOException("Unknown model in release manifest");
                String current = ModelInstaller.current(c, name);
                if (ModelVersion.compare(j.getString("version"), current) == 0) continue;
                ModelRelease release = ModelRelease.parse(
                    j,
                    current,
                    c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode
                );
                String blocked = c
                    .getSharedPreferences("cloud", 0)
                    .getString("blocked." + name, "");
                if (blocked.equals(release.version)) {
                    s.updateMessage(
                        "Rejected model " + name + " " + blocked + "; waiting for a new release"
                    );
                    continue;
                }
                File candidate = new File(dir, name + ".tflite");
                SupabaseClient.transfer(
                    s,
                    cfg.modelBucket,
                    release.path,
                    candidate,
                    false,
                    guard,
                    release.sizeBytes
                );
                guard.check();
                if (
                    candidate.length() != release.sizeBytes ||
                    !TripPackage.hash(candidate).equals(release.sha256)
                ) throw new IOException("Model download integrity check failed");
                synchronized (ModelInstaller.LOCK) {
                    if (SensorCaptureService.instance != null) {
                        File pending = new File(c.getFilesDir(), "cloud-pending-models/" + name);
                        pending.mkdirs();
                        java.nio.file.Files.copy(
                            candidate.toPath(),
                            new File(pending, "candidate.tflite").toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        );
                        TripPackage.save(
                            new File(pending, "release.json"),
                            new JSONObject()
                                .put("release", release.metadata)
                                .put("project", cfg.projectUrl)
                                .put("userId", user)
                        );
                        s.updateMessage("Update ready; apply between trips in Settings");
                        continue;
                    }
                    try {
                        guard.check();
                        ModelInstaller.install(
                            c,
                            name,
                            release.version,
                            release.sha256,
                            candidate,
                            false,
                            true
                        );
                        changed = true;
                    } catch (Exception e) {
                        c.getSharedPreferences("cloud", 0)
                            .edit()
                            .putString("blocked." + name, release.version)
                            .commit();
                        throw e;
                    }
                }
            }
            if (!s.updateMessage().startsWith("Update ready")) s.updateMessage(
                changed
                    ? "Approved models updated; previous versions retained"
                    : "Models are up to date"
            );
            return Result.success();
        } catch (Exception e) {
            s.updateMessage(
                e.getMessage() == null
                    ? "Model update failed; current model retained"
                    : e.getMessage()
            );
            return (e instanceof SupabaseClient.HttpError &&
                ((SupabaseClient.HttpError) e).retry()) ||
                e instanceof java.net.SocketException ||
                e instanceof java.net.SocketTimeoutException ||
                e instanceof java.net.UnknownHostException
                ? Result.retry()
                : Result.failure();
        }
    }
}
