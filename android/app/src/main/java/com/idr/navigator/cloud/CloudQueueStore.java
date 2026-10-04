package com.idr.navigator.cloud;

import java.io.*;
import org.json.*;

/** All state transitions are atomic file replacements; instances share one lock. */
public final class CloudQueueStore {

    private static final Object LOCK = new Object();
    private final File dir;

    public CloudQueueStore(File dir) {
        this.dir = dir;
        dir.mkdirs();
    }

    public void authorizeCompleted(String project, String user, String id, long generation)
        throws Exception {
        synchronized (LOCK) {
            TripPackage.identity(id);
            if (!new File(dir, id + ".json").exists()) enqueue(project, user, id, generation);
        }
    }

    public JSONObject enqueue(String project, String user, String id, long generation)
        throws Exception {
        synchronized (LOCK) {
            TripPackage.identity(id);
            File f = new File(dir, id + ".json");
            JSONObject j = f.exists()
                ? TripPackage.json(f)
                : new JSONObject()
                      .put("sessionId", id)
                      .put("project", project)
                      .put("userId", user)
                      .put("progress", new JSONObject());
            if (
                !project.equals(j.getString("project")) || !user.equals(j.getString("userId"))
            ) throw new IOException("This trip belongs to a different cloud account");
            if (!"uploaded".equals(j.optString("state"))) {
                j.put("generation", generation).put("state", "queued").put("message", "");
                TripPackage.save(f, j);
            }
            return j;
        }
    }

    public JSONArray list(String project, String user) throws Exception {
        synchronized (LOCK) {
            JSONArray a = new JSONArray();
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (files != null) for (File f : files) {
                JSONObject j = TripPackage.json(f);
                if (
                    project.equals(j.optString("project")) && user.equals(j.optString("userId"))
                ) a.put(j);
            }
            return a;
        }
    }

    public void status(JSONObject j, String state, String message) throws Exception {
        synchronized (LOCK) {
            j.put("state", state).put("message", message);
            TripPackage.save(new File(dir, j.getString("sessionId") + ".json"), j);
        }
    }

    public void progress(JSONObject j, String part, String hash) throws Exception {
        synchronized (LOCK) {
            j.getJSONObject("progress").put(part, hash);
            TripPackage.save(new File(dir, j.getString("sessionId") + ".json"), j);
        }
    }
}
