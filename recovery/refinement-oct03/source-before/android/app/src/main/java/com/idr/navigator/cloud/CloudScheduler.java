package com.idr.navigator.cloud;

import android.content.*;
import androidx.work.*;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

public final class CloudScheduler {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    public static final String TAG = "offroute-cloud";

    public static CloudQueueStore queue(Context c) {
        return new CloudQueueStore(new File(c.getFilesDir(), "cloud-queue"));
    }

    private static Constraints constraints(CloudSessionStore s) {
        return new Constraints.Builder()
            .setRequiredNetworkType(s.wifiOnly() ? NetworkType.UNMETERED : NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build();
    }

    public static void schedule(Context c) throws Exception {
        CloudSessionStore s = new CloudSessionStore(c);
        if (s.user().isEmpty()) return;
        WorkManager w = WorkManager.getInstance(c);
        Constraints n = constraints(s);
        if (s.consent()) {
            w.enqueueUniqueWork(
                "cloud-upload-now",
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(CloudUploadWorker.class)
                    .setConstraints(n)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag(TAG)
                    .build()
            );
            w.enqueueUniquePeriodicWork(
                "cloud-upload-reconcile",
                ExistingPeriodicWorkPolicy.UPDATE,
                new PeriodicWorkRequest.Builder(CloudUploadWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(n)
                    .addTag(TAG)
                    .build()
            );
        }
        w.enqueueUniquePeriodicWork(
            "cloud-model-check",
            ExistingPeriodicWorkPolicy.UPDATE,
            new PeriodicWorkRequest.Builder(ModelUpdateWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(n)
                .addTag(TAG)
                .build()
        );
    }

    public static void cancel(Context c) {
        WorkManager.getInstance(c).cancelAllWorkByTag(TAG);
    }

    public static void preferences(Context c, boolean consent, boolean wifi) throws Exception {
        cancel(c);
        CloudSessionStore s = new CloudSessionStore(c);
        s.preferences(consent, wifi);
        reconcile(c);
        schedule(c);
    }

    public static void enqueue(Context c, String id) throws Exception {
        CloudSessionStore s = new CloudSessionStore(c);
        synchronized (CloudSessionStore.LOCK) {
            if (!s.consent() || s.user().isEmpty()) throw new IOException(
                "Sign in and enable trip contribution first"
            );
            TripPackage.identity(id);
            queue(c).enqueue(s.config().projectUrl, s.user(), id, s.generation());
        }
        schedule(c);
    }

    public static void began(Context c, String id) {
        try {
            CloudSessionStore s = new CloudSessionStore(c);
            synchronized (CloudSessionStore.LOCK) {
                if (s.consent() && !s.user().isEmpty()) TripPackage.save(
                    new File(c.getFilesDir(), "cloud-eligible/" + id + ".json"),
                    new JSONObject()
                        .put("project", s.config().projectUrl)
                        .put("userId", s.user())
                        .put("generation", s.generation())
                );
            }
        } catch (Exception ignored) {
            /* Cloud failure never stops local navigation. */
        }
    }

    public static void completed(Context c, String id) {
        IO.execute(() -> {
            try {
                reconcile(c);
                schedule(c);
            } catch (Exception ignored) {}
        });
    }

    public static void reconcile(Context c) throws Exception {
        CloudSessionStore s = new CloudSessionStore(c);
        synchronized (CloudSessionStore.LOCK) {
            if (!s.consent() || s.user().isEmpty()) return;
            File[] tickets = new File(c.getFilesDir(), "cloud-eligible").listFiles();
            if (tickets != null) for (File f : tickets) {
                if (!f.getName().endsWith(".json")) continue;
                JSONObject t = TripPackage.json(f);
                String id = f.getName().replace(".json", "");
                if (
                    s.user().equals(t.optString("userId")) &&
                    s.config().projectUrl.equals(t.optString("project")) &&
                    s.generation() == t.optLong("generation") &&
                    new File(c.getFilesDir(), "drives/" + id + ".summary.json").isFile()
                ) queue(c).authorizeCompleted(s.config().projectUrl, s.user(), id, s.generation());
            }
            // Only already-authorized queued sessions are resumed, never a directory scan of old recordings.
            JSONArray entries = queue(c).list(s.config().projectUrl, s.user());
            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.getJSONObject(i);
                if (
                    !"uploaded".equals(e.optString("state")) &&
                    !"action-required".equals(e.optString("state"))
                ) queue(c).enqueue(
                    s.config().projectUrl,
                    s.user(),
                    e.getString("sessionId"),
                    s.generation()
                );
            }
        }
    }

    public static void modelCheck(Context c) throws Exception {
        CloudSessionStore s = new CloudSessionStore(c);
        if (s.user().isEmpty()) throw new IOException("Sign in first");
        WorkManager.getInstance(c).enqueueUniqueWork(
            "cloud-model-now",
            ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(ModelUpdateWorker.class)
                .setConstraints(constraints(s))
                .addTag(TAG)
                .build()
        );
        s.updateMessage("Model check queued");
    }

    public static void startup(Context c) {
        IO.execute(() -> {
            try {
                reconcile(c);
                schedule(c);
            } catch (Exception ignored) {}
        });
    }
}
