package com.nagham.player;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Safety net: every crash is written to a report file before the process dies (so it can be shared and fixed),
 * non-fatal errors caught by the app's own guards are logged to the same file, and the next start offers to share
 * the report. Nothing is swallowed silently.
 */
public final class CrashGuard {
    private CrashGuard() {
    }

    private static final String FILE = "crash_report.txt";
    private static final int MAX = 48 * 1024;
    private static Application app;

    public static void install(Application a) {
        app = a;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                append("CRASH on thread " + t.getName(), e);
                Store.prefs(a).edit().putBoolean("crash_unseen", true).commit();
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
            else System.exit(2);
        });
    }

    /** A problem the app recovered from by itself: logged, the app keeps running. */
    public static void nonFatal(String where, Throwable t) {
        Log.w("Nagham", where, t);
        try {
            append("recovered: " + where, t);
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void append(String title, Throwable t) throws Exception {
        if (app == null) return;
        File f = new File(app.getFilesDir(), FILE);
        StringBuilder sb = new StringBuilder();
        sb.append("==== ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("  ").append(title).append('\n');
        sb.append("Nagham ").append(version()).append(" | Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT)
                .append(") | ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append(Log.getStackTraceString(t)).append('\n');
        try (FileOutputStream o = new FileOutputStream(f, true)) {
            o.write(sb.toString().getBytes("UTF-8"));
        }
        if (f.length() > MAX) {                       // keep the newest part
            byte[] all = new byte[(int) f.length()];
            try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                r.readFully(all);
            }
            try (FileOutputStream o = new FileOutputStream(f, false)) {
                o.write(all, all.length - MAX / 2, MAX / 2);
            }
        }
    }

    private static String version() {
        try {
            return app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static String report(Context c) {
        try {
            File f = new File(c.getApplicationContext().getFilesDir(), FILE);
            if (!f.exists()) return "";
            byte[] b = new byte[(int) f.length()];
            try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                r.readFully(b);
            }
            return new String(b, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean has(Context c) {
        File f = new File(c.getApplicationContext().getFilesDir(), FILE);
        return f.exists() && f.length() > 0;
    }

    public static void share(Context c) {
        String r = report(c);
        if (r.isEmpty()) return;
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Nagham crash report").putExtra(Intent.EXTRA_TEXT, r);
        Ui.go(c, Intent.createChooser(i, c.getString(R.string.crash_share)));
    }

    /** After a crash, the next start asks once whether to share the report. */
    public static void offer(final Activity a) {
        if (!Store.prefs(a).getBoolean("crash_unseen", false) || !has(a)) return;
        Store.prefs(a).edit().putBoolean("crash_unseen", false).apply();
        Dlg.confirm(a, a.getString(R.string.crash_title), a.getString(R.string.crash_msg), R.string.crash_share, false, () -> share(a));
    }
}
