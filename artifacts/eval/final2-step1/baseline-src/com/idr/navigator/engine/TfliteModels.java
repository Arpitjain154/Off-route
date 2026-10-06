package com.idr.navigator.engine;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;
import org.tensorflow.lite.Interpreter;

/** IO-VNBD models, with separate training provenance and device-validation gates. */
public final class TfliteModels implements AutoCloseable {
    // TODO(device-validation): evaluate phone/tunnel drives before enabling learned fusion corrections.

    public static final String[] NAMES = { "motion", "gps-health", "speed" };
    private final Map<String, Interpreter> models = new HashMap<>();
    private final Context context;
    private final File directory;

    public TfliteModels(Context c) throws IOException {
        context = c;
        directory = new File(c.getFilesDir(), "models");
        directory.mkdirs();
        JSONObject bundled;
        try (InputStream in = c.getAssets().open("models/manifest.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
            bundled = new JSONObject(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (JSONException e) { throw new IOException("Invalid bundled model manifest", e); }
        try {
        for (String name : NAMES) {
            File f = new File(directory, name + ".tflite");
            android.content.SharedPreferences prefs = c.getSharedPreferences("models", 0);
            // Migrate the old bundled constant models, preserving any later OTA installation.
            JSONObject entry=null;JSONArray bundleEntries=bundled.optJSONArray("models");
            for(int i=0;bundleEntries!=null&&i<bundleEntries.length();i++) {
                JSONObject candidate=bundleEntries.optJSONObject(i);
                if(candidate!=null&&name.equals(candidate.optString("name")))entry=candidate;
            }
            boolean installBundle = !f.exists() || (entry!=null && ModelVersion.compare(entry.optString("version","0.0.0"),
                prefs.getString(name + ".version", "0.0.0"))>0);
            if (installBundle) try (
                InputStream in = c.getAssets().open("models/" + name + ".tflite");
                OutputStream out = new FileOutputStream(f)
            ) {
                byte[] copyBuffer = new byte[65536];
                int copyCount;
                while ((copyCount = in.read(copyBuffer)) != -1) out.write(copyBuffer, 0, copyCount);
            }
            if (installBundle) {
                JSONArray entries = bundled.optJSONArray("models");
                for (int i = 0; entries != null && i < entries.length(); i++) {
                    JSONObject m = entries.optJSONObject(i);
                    if (m != null && name.equals(m.optString("name"))) prefs.edit()
                        .putString(name + ".version", m.optString("version", "1.0.0"))
                        .putBoolean(name + ".placeholder", m.optBoolean("placeholder", true))
                        .putBoolean(name + ".validated", m.optBoolean("validated", false)).apply();
                }
            }
            models.put(name, openValidated(f, name));
        }
        } catch (IOException | RuntimeException | LinkageError e) {
            close();
            throw e;
        }
    }

    public static Interpreter openValidated(File file, String name) {
        Interpreter model = new Interpreter(file, new Interpreter.Options().setNumThreads(2));
        int[] expected = name.equals("motion")
            ? new int[] { 1, 42 }
            : name.equals("gps-health")
              ? new int[] { 1, 10 }
              : new int[] { 1, 300, 6 };
        int[] output = { 1, name.equals("motion") ? 5 : name.equals("gps-health") ? 3 : 1 };
        if (
            !Arrays.equals(model.getInputTensor(0).shape(), expected) ||
            !Arrays.equals(model.getOutputTensor(0).shape(), output)
        ) {
            model.close();
            throw new IllegalArgumentException("Model tensor shape mismatch: " + name);
        }
        return model;
    }

    public synchronized float[] motion(FeatureBuffer f) {
        float[][] result = new float[1][5];
        models.get("motion").run(f.motion(), result);
        return result[0];
    }

    public synchronized float[] health(float[] features) {
        float[][] result = new float[1][3];
        models.get("gps-health").run(new float[][] { features }, result);
        return result[0];
    }

    public synchronized float speed(FeatureBuffer f) {
        float[][] result = new float[1][1];
        models.get("speed").run(f.imu(), result);
        return result[0][0];
    }

    public boolean placeholder(String name) {
        return context.getSharedPreferences("models", 0).getBoolean(name + ".placeholder", true);
    }

    public boolean enabled(String name) {
        return !placeholder(name) && context.getSharedPreferences("models", 0).getBoolean(name + ".validated", false);
    }

    public synchronized void reload(String name, File candidate) throws IOException {
        Interpreter next = openValidated(candidate, name);
        next.close();
        File dest = new File(directory, name + ".tflite");
        java.nio.file.Files.move(
            candidate.toPath(),
            dest.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE
        );
        Interpreter previous = models.put(name, openValidated(dest, name));
        if (previous != null) previous.close();
    }

    public synchronized void close() {
        for (Interpreter model : models.values()) model.close();
        models.clear();
    }

    public static int argmax(float[] x) {
        int best = 0;
        for (int i = 1; i < x.length; i++) if (x[i] > x[best]) best = i;
        return best;
    }
}
