package com.idr.navigator.cloud;

import com.idr.navigator.engine.ModelVersion;
import java.io.*;
import java.util.*;
import org.json.*;

public final class ModelRelease {

    public final String name, version, path, sha256;
    public final long sizeBytes;
    public final JSONObject metadata;

    private ModelRelease(JSONObject j) throws Exception {
        name = j.getString("name");
        version = j.getString("version");
        path = j.getString("path");
        sha256 = j.getString("sha256");
        sizeBytes = j.getLong("sizeBytes");
        metadata = j;
    }

    public static ModelRelease parse(JSONObject j, String current, int appCode) throws IOException {
        try {
            String n = j.getString("name"),
                v = j.getString("version");
            int[] input = n.equals("speed")
                ? new int[] { 1, 300, 6 }
                : new int[] { 1, n.equals("motion") ? 42 : 10 };
            int[] output = { 1, n.equals("motion") ? 5 : n.equals("gps-health") ? 3 : 1 };
            if (
                !Arrays.asList("speed", "motion", "gps-health").contains(n) ||
                !v.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)") ||
                ModelVersion.compare(v, current) <= 0 ||
                j.getInt("schemaVersion") != 1 ||
                !j.getBoolean("approved") ||
                !j.getBoolean("validated") ||
                j.getInt("minAppVersionCode") > appCode ||
                !"idr-iovnbd-v1".equals(j.getString("featureContract")) ||
                !"float32".equals(j.getString("dtype")) ||
                !matches(j.getJSONArray("inputShape"), input) ||
                !matches(j.getJSONArray("outputShape"), output) ||
                !j.getString("path").equals(n + "/" + v + "/" + n + ".tflite") ||
                !j.getString("sha256").matches("[a-f0-9]{64}") ||
                j.getLong("sizeBytes") < 8 ||
                j.getLong("sizeBytes") > 50 * 1024 * 1024
            ) throw new IOException("Model release is unapproved, incompatible or not newer");
            return new ModelRelease(j);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Invalid model release", e);
        }
    }

    private static boolean matches(JSONArray a, int[] b) throws Exception {
        if (a.length() != b.length) return false;
        for (int i = 0; i < b.length; i++) if (a.getInt(i) != b[i]) return false;
        return true;
    }
}
