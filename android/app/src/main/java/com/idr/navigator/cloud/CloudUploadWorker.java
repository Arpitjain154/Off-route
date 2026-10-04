package com.idr.navigator.cloud;

import android.content.Context;
import androidx.work.*;
import java.io.*;
import org.json.*;

public final class CloudUploadWorker extends Worker {

    private static final Object UPLOAD_LOCK = new Object();

    public CloudUploadWorker(Context c, WorkerParameters p) {
        super(c, p);
    }

    @Override
    public Result doWork() {
        synchronized (UPLOAD_LOCK) {
            Context c = getApplicationContext();
            CloudSessionStore s = new CloudSessionStore(c);
            boolean retry = false;
            try {
                if (!s.consent() || s.user().isEmpty()) return Result.success();
                CloudScheduler.reconcile(c);
                String user = s.user();
                long generation = s.generation();
                CloudConfig cfg = s.config();
                CloudQueueStore q = CloudScheduler.queue(c);
                JSONArray entries = q.list(cfg.projectUrl, user);
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject item = entries.getJSONObject(i);
                    if (
                        "uploaded".equals(item.optString("state")) ||
                        "action-required".equals(item.optString("state"))
                    ) continue;
                    SupabaseClient.Guard guard = () -> {
                        if (isStopped()) throw new InterruptedIOException("Upload paused");
                        s.guard(user, generation, true);
                        if (
                            !cfg.projectUrl.equals(s.config().projectUrl) ||
                            item.getLong("generation") != generation
                        ) throw new IOException("Cloud account changed");
                    };
                    try {
                        guard.check();
                        String id = item.getString("sessionId");
                        q.status(item, "uploading", "");
                        File stage = new File(c.getFilesDir(), "cloud-staging/" + id);
                        JSONObject manifest = TripPackage.prepare(
                            new File(c.getFilesDir(), "drives"),
                            id,
                            stage,
                            TripPackage.CHUNK_BYTES
                        );
                        JSONArray files = manifest.getJSONArray("files");
                        for (int f = 0; f < files.length(); f++) {
                            JSONArray chunks = files.getJSONObject(f).getJSONArray("chunks");
                            for (int n = 0; n < chunks.length(); n++) {
                                JSONObject chunk = chunks.getJSONObject(n);
                                String name = chunk.getString("objectName"),
                                    hash = chunk.getString("sha256");
                                guard.check();
                                if (
                                    hash.equals(item.getJSONObject("progress").optString(name))
                                ) continue;
                                File part = new File(stage, name);
                                verifiedUpload(s, cfg, user + "/" + id + "/" + name, part, guard);
                                guard.check();
                                q.progress(item, name, hash);
                            }
                        }
                        verifiedUpload(
                            s,
                            cfg,
                            user + "/" + id + "/complete.json",
                            new File(stage, "complete.json"),
                            guard
                        );
                        guard.check();
                        q.status(item, "uploaded", "");
                        File[] staged = stage.listFiles();
                        if (staged != null) for (File f : staged) if (f.isFile()) f.delete();
                        stage.delete();
                    } catch (Exception e) {
                        boolean temporary = CloudFailures.retry(e);
                        // Never overwrite a newer account/consent generation with this worker's result.
                        synchronized (CloudSessionStore.LOCK) {
                            if (s.generation() == generation) q.status(
                                item,
                                temporary ? "queued" : "action-required",
                                e.getMessage() == null ? "Upload failed; retry" : e.getMessage()
                            );
                        }
                        retry |= temporary;
                        if (isStopped()) break;
                    }
                }
                return retry ? Result.retry() : Result.success();
            } catch (Exception e) {
                return Result.retry();
            }
        }
    }

    private void verifiedUpload(
        CloudSessionStore s,
        CloudConfig cfg,
        String path,
        File file,
        SupabaseClient.Guard guard
    ) throws Exception {
        SupabaseClient.transfer(s, cfg.journeyBucket, path, file, true, guard, file.length());
        File check = new File(file.getPath() + ".verify");
        try {
            SupabaseClient.transfer(s, cfg.journeyBucket, path, check, false, guard, file.length());
            if (!TripPackage.hash(file).equals(TripPackage.hash(check))) throw new IOException(
                "Uploaded file checksum mismatch"
            );
        } finally {
            check.delete();
        }
    }
}
