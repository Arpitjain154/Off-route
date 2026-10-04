package com.idr.navigator.engine;

import android.content.*;
import com.idr.navigator.cloud.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import org.json.*;
import org.tensorflow.lite.*;

public final class ModelInstaller {

    public static final Object LOCK = new Object();

    private static ModelTransaction transaction(Context c) {
        SharedPreferences p = c.getSharedPreferences("models", 0);
        return new ModelTransaction(
            new File(c.getFilesDir(), "models"),
            new ModelTransaction.Metadata() {
                public JSONObject read(String n) throws Exception {
                    return new JSONObject()
                        .put("version", p.getString(n + ".version", "0.0.0"))
                        .put("placeholder", p.getBoolean(n + ".placeholder", true))
                        .put("validated", p.getBoolean(n + ".validated", false));
                }

                public void write(String n, JSONObject j) throws Exception {
                    if (
                        !p
                            .edit()
                            .putString(n + ".version", j.getString("version"))
                            .putBoolean(n + ".placeholder", j.optBoolean("placeholder", false))
                            .putBoolean(n + ".validated", j.optBoolean("validated", false))
                            .commit()
                    ) throw new IOException("Cannot persist model metadata");
                }
            }
        );
    }

    public static void recover(Context c) throws Exception {
        synchronized (LOCK) {
            transaction(c).recover();
        }
    }

    public static String current(Context c, String name) throws Exception {
        String v = c.getSharedPreferences("models", 0).getString(name + ".version", "0.0.0");
        try (InputStream in = c.getAssets().open("models/manifest.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] x = new byte[4096];
            int n;
            while ((n = in.read(x)) != -1) b.write(x, 0, n);
            JSONArray a = new JSONObject(b.toString("UTF-8")).getJSONArray("models");
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                if (
                    name.equals(j.getString("name")) &&
                    ModelVersion.compare(j.getString("version"), v) > 0
                ) v = j.getString("version");
            }
        }
        return v;
    }

    public static void install(
        Context c,
        String name,
        String version,
        String hash,
        File source,
        boolean placeholder,
        boolean validated
    ) throws Exception {
        synchronized (LOCK) {
            if (SensorCaptureService.instance != null) throw new IOException(
                "Stop tracking before installing model updates"
            );
            if (
                !java.util.Arrays.asList(TfliteModels.NAMES).contains(name) ||
                !version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)") ||
                ModelVersion.compare(version, current(c, name)) <= 0
            ) throw new IOException("Model must be newer than the installed version");
            if (
                !hash.matches("[a-f0-9]{64}") ||
                source.length() > 50 * 1024 * 1024 ||
                !hash.equals(TripPackage.hash(source))
            ) throw new IOException("Model checksum or size mismatch");
            File dir = new File(c.getFilesDir(), "models");
            dir.mkdirs();
            File staged = new File(dir, name + ".candidate");
            ModelTransaction.stage(source,staged,hash);
            try (Interpreter model = TfliteModels.openValidated(staged, name)) {
                if (
                    model.getInputTensor(0).dataType() != DataType.FLOAT32 ||
                    model.getOutputTensor(0).dataType() != DataType.FLOAT32
                ) throw new IOException("Model data type mismatch");
                ByteBuffer in = ByteBuffer.allocateDirect(model.getInputTensor(0).numBytes()).order(
                    ByteOrder.nativeOrder()
                );
                ByteBuffer out = ByteBuffer.allocateDirect(
                    model.getOutputTensor(0).numBytes()
                ).order(ByteOrder.nativeOrder());
                model.run(in, out);
                out.rewind();
                while (out.remaining() >= 4)
                    if (!Float.isFinite(out.getFloat())) throw new IOException(
                        "Model smoke test returned invalid predictions"
                    );
            }
            transaction(c).install(
                name,
                staged,
                new JSONObject()
                    .put("version", version)
                    .put("placeholder", placeholder)
                    .put("validated", validated)
            );
        }
    }
}
