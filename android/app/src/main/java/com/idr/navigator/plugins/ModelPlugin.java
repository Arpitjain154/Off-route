package com.idr.navigator.plugins;

import android.content.SharedPreferences;
import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.idr.navigator.engine.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import org.tensorflow.lite.Interpreter;

@CapacitorPlugin(name = "ModelPlugin")
public final class ModelPlugin extends Plugin {

    @PluginMethod
    public void checkForUpdate(PluginCall call) {
        JSArray models = new JSArray();
        SharedPreferences p = getContext().getSharedPreferences("models", 0);
        org.json.JSONObject bundle = new org.json.JSONObject();
        try (InputStream in = getContext().getAssets().open("models/manifest.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
            bundle = new org.json.JSONObject(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) { call.reject("Cannot read model manifest", e); return; }
        for (String name : TfliteModels.NAMES) {
            JSObject m = new JSObject();
            m.put("name", name);
            String version = p.getString(name + ".version", "0.0.0");
            boolean oldBundle = version.equals("0.0.0");
            org.json.JSONObject entry=new org.json.JSONObject();
            org.json.JSONArray entries=bundle.optJSONArray("models");
            for(int i=0;entries!=null&&i<entries.length();i++) {
                org.json.JSONObject e=entries.optJSONObject(i);
                if(e!=null&&name.equals(e.optString("name")))entry=e;
            }
            m.put("version", oldBundle ? entry.optString("version", "0.0.0") : version);
            m.put("placeholder", oldBundle ? entry.optBoolean("placeholder",true) : p.getBoolean(name + ".placeholder", true));
            m.put("validated", p.getBoolean(name + ".validated", false));
            models.put(m);
        }
        JSObject result = new JSObject();
        result.put("models", models);
        call.resolve(result);
    }

    // Internet transfer stays in Axios/JS. Native validates and atomically installs the staged binary.
    @PluginMethod
    public void downloadModel(PluginCall call) {
        String name = call.getString("name", ""),
            path = call.getString("path", ""),
            version = call.getString("version", ""),
            sha = call.getString("sha256", "");
        if (!java.util.Arrays.asList(TfliteModels.NAMES).contains(name)) {
            call.reject("Unknown model");
            return;
        }
        getBridge().execute(() -> {
            try {
                File source = new File(java.net.URI.create(path)).getCanonicalFile();
                if (!source.toPath().startsWith(getContext().getCacheDir().getCanonicalFile().toPath())) throw new IOException("Invalid staged model");
                ModelInstaller.install(getContext(),name,version,sha,source,call.getBoolean("placeholder",true),call.getBoolean("validated",false));
                source.delete();call.resolve();
            } catch(Exception e) { call.reject(e.getMessage()); }
        });
    }
}
