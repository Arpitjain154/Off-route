package com.idr.navigator.cloud;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.io.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** One process-wide lock serializes refresh token rotation and configuration changes. */
public final class CloudSessionStore {

    public static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    public static final CloudRequestGate REQUESTS = new CloudRequestGate(LOCK);

    public CloudSessionStore(Context c) {
        prefs = c.getSharedPreferences("cloud", 0);
    }

    public CloudConfig config() throws IOException {
        return new CloudConfig(
            prefs.getString("url", CloudConfig.URL),
            prefs.getString("key", CloudConfig.KEY),
            prefs.getString("bucket", "journeys"),
            "model-releases"
        );
    }

    public void configure(String url, String key, String bucket) throws Exception {
        synchronized (LOCK) {
            new CloudConfig(url, key, bucket, "model-releases");
            signOut();
            prefs
                .edit()
                .putString("url", url)
                .putString("key", key)
                .putString("bucket", bucket)
                .commit();
        }
    }

    public long generation() {
        return prefs.getLong("generation", 0);
    }

    public boolean consent() {
        return prefs.getBoolean("consent", false);
    }

    public boolean wifiOnly() {
        return prefs.getBoolean("wifiOnly", true);
    }

    public void preferences(boolean consent, boolean wifi) {
        REQUESTS.invalidate(() ->
            prefs
                .edit()
                .putBoolean("consent", consent)
                .putBoolean("wifiOnly", wifi)
                .putLong("generation", generation() + 1)
                .commit()
        );
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias("offroute-cloud")) {
            KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            g.init(
                new KeyGenParameterSpec.Builder(
                    "offroute-cloud",
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            );
            g.generateKey();
        }
        return (SecretKey) ks.getKey("offroute-cloud", null);
    }

    public JSONObject session() throws Exception {
        synchronized (LOCK) {
            String v = prefs.getString("session", "");
            if (v.isEmpty()) return null;
            try {
                byte[] blob = Base64.decode(v, Base64.NO_WRAP);
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, blob, 0, 12));
                return new JSONObject(
                    new String(
                        c.doFinal(blob, 12, blob.length - 12),
                        java.nio.charset.StandardCharsets.UTF_8
                    )
                );
            } catch (Exception e) {
                signOut();
                throw new IOException("Cloud login expired. Sign in again.");
            }
        }
    }

    private void save(JSONObject s) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(c.getIV());
        b.write(c.doFinal(s.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (
            !prefs
                .edit()
                .putString("session", Base64.encodeToString(b.toByteArray(), Base64.NO_WRAP))
                .commit()
        ) throw new IOException("Cannot save cloud login");
    }

    public String signIn(String email, String password, boolean signup) throws Exception {
        synchronized (LOCK) {
            JSONObject result = SupabaseClient.auth(
                config(),
                signup ? "signup" : "token?grant_type=password",
                new JSONObject().put("email", email.trim()).put("password", password)
            );
            if (
                !result.has("access_token")
            ) return "Check your email to confirm the account, then sign in.";
            signOut();
            result.put(
                "expires_at",
                System.currentTimeMillis() / 1000 + result.optLong("expires_in", 3600)
            );
            save(result);
            return "Signed in. Enable trip contribution to upload.";
        }
    }

    public JSONObject validSession(boolean force) throws Exception {
        synchronized (LOCK) {
            JSONObject s = session();
            if (s == null) throw new IOException("Sign in to Supabase first");
            if (force || s.optLong("expires_at") < System.currentTimeMillis() / 1000 + 60) {
                try {
                    s = SupabaseClient.auth(
                        config(),
                        "token?grant_type=refresh_token",
                        new JSONObject().put("refresh_token", s.getString("refresh_token"))
                    );
                    s.put(
                        "expires_at",
                        System.currentTimeMillis() / 1000 + s.optLong("expires_in", 3600)
                    );
                    save(s);
                } catch (SupabaseClient.HttpError e) {
                    if (e.status == 400 || e.status == 401) signOut();
                    throw e;
                }
            }
            return s;
        }
    }

    public void signOut() {
        REQUESTS.invalidate(() ->
            prefs
                .edit()
                .remove("session")
                .putBoolean("consent", false)
                .putLong("generation", generation() + 1)
                .commit()
        );
    }

    public String user() throws Exception {
        JSONObject s = session();
        return s == null ? "" : s.getJSONObject("user").getString("id");
    }

    public void guard(String user, long generation, boolean upload) throws Exception {
        if (
            !CloudConfig.authorized(user, user(), generation, generation(), !upload || consent())
        ) throw new IOException(
            "Cloud access changed; retry after signing in and enabling contribution"
        );
    }

    public void updateMessage(String s) {
        prefs.edit().putString("modelMessage", s).apply();
    }

    public String updateMessage() {
        return prefs.getString("modelMessage", "No cloud model checked yet");
    }
}
