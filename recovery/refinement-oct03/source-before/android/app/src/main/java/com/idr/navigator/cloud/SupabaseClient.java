package com.idr.navigator.cloud;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

public final class SupabaseClient {

    public static class HttpError extends IOException {

        public final int status;

        HttpError(int s) {
            super(
                s == 401
                    ? "Cloud login needs renewal"
                    : s == 403
                      ? "Storage access denied; check account and policies"
                      : s == 413
                        ? "Upload exceeds bucket limit"
                        : s == 429
                          ? "Cloud rate limit; retrying later"
                          : "Cloud request failed (" + s + ")"
            );
            status = s;
        }

        public boolean retry() {
            return status == 429 || status >= 500;
        }

        HttpError(int s, String message) {
            super(message);
            status = s;
        }
    }

    // Only show known messages, never raw server bodies that may contain personal data.
    static HttpError authError(int status, String body) {
        String code = "";
        try { code = new JSONObject(body).optString("error_code"); } catch (Exception ignored) {}
        String message;
        switch (code) {
            case "invalid_credentials":
                message = "Incorrect email or password. New to OFF-ROUTE? Choose Create account first; your Supabase dashboard login is separate.";
                break;
            case "email_not_confirmed":
                message = "Confirm your email using the signup email, then sign in. Check your spam folder too.";
                break;
            case "signup_disabled": case "email_provider_disabled":
                message = "Email registration is disabled for this project. Contact the app administrator.";
                break;
            case "email_address_not_authorized":
                message = "This project's email service cannot send to this address yet. The administrator must configure an email provider.";
                break;
            case "weak_password":
                message = "Choose a stronger password that meets the project's password requirements.";
                break;
            case "user_already_exists": case "email_exists":
                message = "This account already exists. Use Sign in.";
                break;
            case "over_email_send_rate_limit": case "over_request_rate_limit":
                message = "Too many attempts. Please wait before trying again.";
                break;
            default:
                message = status == 429 ? "Too many attempts. Please wait before trying again."
                    : status == 401 ? "The project key was rejected. Check the Supabase connection."
                    : status >= 500 ? "Cloud sign-in is temporarily unavailable. Try again later."
                    : "Sign-in or registration failed (" + status + "). Check your details; use Create account if you have not registered in OFF-ROUTE.";
        }
        return new HttpError(status, message);
    }

    public interface Guard {
        void check() throws Exception;
    }

    private static HttpURLConnection open(CloudConfig cfg, String path, String token, String method)
        throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(cfg.projectUrl + path).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod(method);
        c.setRequestProperty("apikey", cfg.publishableKey);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }

    public static JSONObject auth(CloudConfig cfg, String path, JSONObject body) throws Exception {
        HttpURLConnection c = open(cfg, "/auth/v1/" + path, null, "POST");
        try {
            byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setFixedLengthStreamingMode(b.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(b);
            }
            int status = c.getResponseCode();
            if (status < 200 || status >= 300) {
                String errorBody = "";
                try {
                    InputStream error = c.getErrorStream();
                    if (error != null) errorBody = new String(read(error, 16384), StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
                throw authError(status, errorBody);
            }
            return new JSONObject(
                new String(read(c.getInputStream(), 1024 * 1024), StandardCharsets.UTF_8)
            );
        } finally {
            c.disconnect();
        }
    }

    private static byte[] read(InputStream in, int limit) throws IOException {
        try (InputStream source = in; ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = source.read(buf)) != -1) {
                if (b.size() + n > limit) throw new IOException("Cloud response too large");
                b.write(buf, 0, n);
            }
            return b.toByteArray();
        }
    }

    public static String path(String bucket, String object) throws IOException {
        if (
            !bucket.matches("[a-z0-9-]+") ||
            !object.matches("[a-zA-Z0-9._/-]+") ||
            object.contains("..") ||
            object.startsWith("/")
        ) throw new IOException("Invalid cloud object path");
        return bucket + "/" + object;
    }

    public static void transfer(
        CloudSessionStore store,
        String bucket,
        String object,
        File file,
        boolean upload,
        Guard guard,
        long maxBytes
    ) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection c;
            synchronized (CloudSessionStore.LOCK) {
                guard.check();
                JSONObject session = store.validSession(attempt > 0);
                guard.check();
                c = open(
                    store.config(),
                    "/storage/v1/object/" + (upload ? "" : "authenticated/") + path(bucket, object),
                    session.getString("access_token"),
                    upload ? "POST" : "GET"
                );
            }
            OutputStream[] output = { null };
            int[] response = { 0 };
            if (upload) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/octet-stream");
                c.setRequestProperty("x-upsert", "true");
                c.setFixedLengthStreamingMode(file.length());
            }
            try (
                AutoCloseable lease = CloudSessionStore.REQUESTS.begin(
                    guard::check,
                    c::disconnect,
                    () -> {
                        if (upload) output[0] = c.getOutputStream();
                        else response[0] = c.getResponseCode();
                    }
                )
            ) {
                if (upload) {
                    try (InputStream in = new FileInputStream(file); OutputStream out = output[0]) {
                        byte[] b = new byte[65536];
                        int n;
                        while ((n = in.read(b)) != -1) {
                            guard.check();
                            out.write(b, 0, n);
                        }
                    }
                }
                int status = upload ? c.getResponseCode() : response[0];
                if (status == 401 && attempt == 0) continue;
                if (status < 200 || status >= 300) throw new HttpError(status);
                if (!upload) {
                    file.getParentFile().mkdirs();
                    try (
                        InputStream in = c.getInputStream();
                        OutputStream out = new FileOutputStream(file)
                    ) {
                        byte[] b = new byte[65536];
                        long total = 0;
                        int n;
                        while ((n = in.read(b)) != -1) {
                            guard.check();
                            total += n;
                            if (total > maxBytes) throw new IOException(
                                "Downloaded object exceeds expected size"
                            );
                            out.write(b, 0, n);
                        }
                    }
                }
                guard.check();
                return;
            } finally {
                c.disconnect();
            }
        }
        throw new HttpError(401);
    }
}
