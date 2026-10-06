package com.idr.navigator.engine;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Local crash/startup evidence. Exported only through the user's Android share sheet. */
public final class EngineDiagnostics {
    private EngineDiagnostics() {}

    public static void install(Context context) {
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { failure(app, "Uncaught error on " + thread.getName(), error); }
            catch (RuntimeException | OutOfMemoryError ignored) {}
            finally {
                // Preserve Android's crash handling; never resume an inconsistent process.
                if (previous != null) previous.uncaughtException(thread, error);
            }
        });
    }

    public static void stage(Context context, String stage) {
        context.getSharedPreferences("engine-diagnostics", 0).edit()
            .putString("stage", stage).putLong("time", System.currentTimeMillis()).apply();
    }

    public static void failure(Context context, String stage, Throwable error) {
        Log.e("IDR-Engine", stage, error);
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(new File(context.getFilesDir(), "engine-last-error.txt")),
                StandardCharsets.UTF_8))) {
            out.println(stage);
            out.println("Time: " + System.currentTimeMillis());
            error.printStackTrace(out);
        } catch (IOException ignored) { /* Android logcat still retains the failure. */ }
    }

    public static void export(Context context, OutputStream stream) throws IOException {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
        out.println("IDR engine diagnostic report");
        out.println("Phone: " + Build.MANUFACTURER + " " + Build.MODEL);
        out.println("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        out.println("ABIs: " + String.join(", ", Build.SUPPORTED_ABIS));
        out.println("Java heap limit: " + Runtime.getRuntime().maxMemory());
        try { out.println("App: " + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName); }
        catch (Exception ignored) {}
        var prefs = context.getSharedPreferences("engine-diagnostics", 0);
        out.println("Last stage: " + prefs.getString("stage", "Not started"));
        out.println("Stage time: " + prefs.getLong("time", 0));
        out.flush();
        File saved = new File(context.getFilesDir(), "engine-last-error.txt");
        if (saved.isFile()) try (InputStream in = new FileInputStream(saved)) { copyBounded(in, stream); }
        if (Build.VERSION.SDK_INT >= 30) {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            try {
                for (var exit : manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 4)) {
                    out.println("\nPrevious process exit: reason=" + exit.getReason() + ", status=" + exit.getStatus()
                        + ", time=" + exit.getTimestamp() + ", description=" + exit.getDescription());
                    out.flush();
                    try (InputStream trace = exit.getTraceInputStream()) {
                        if (trace != null) copyBounded(trace, stream);
                    } catch (IOException e) { out.println("Exit trace unavailable: " + e.getMessage()); }
                }
            } catch (RuntimeException e) { out.println("Exit history unavailable: " + e.getMessage()); }
        }
        out.flush();
    }

    private static void copyBounded(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        int left = 65_536, n;
        while (left > 0 && (n = in.read(buffer, 0, Math.min(buffer.length, left))) != -1) {
            out.write(buffer, 0, n); left -= n;
        }
    }
}
