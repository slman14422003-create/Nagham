package com.simomusic.player;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-app updates from the GitHub Releases of {@link #REPO}: checks the latest release, downloads its .apk into the
 * app cache, verifies it is this app, and hands it to the system installer. Android requires the new apk to be signed
 * with the same key as the installed one (the permanent key of the Build Release workflow).
 */
public final class Updater {
    private Updater() {
    }

    /** https://github.com/slman14422003-create/Semo-music (the repository must be public for the check to work). */
    public static final String REPO = "slman14422003-create/Semo-music";

    public static final int E_NET = 1, E_NONE = 2, E_LIMIT = 3, E_FILE = 4;

    public static final class Info {
        public String tag = "", name = "", notes = "", url = "";
        public long size;
        public boolean newer;
    }

    public interface CheckCb {
        void done(Info info, int error);
    }

    public interface DownloadCb {
        void progress(int percent);

        void done(File apk, int error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static String current(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return p.versionName == null ? "?" : p.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** 1.0.12 vs v1.0.9 -> compares number by number; anything that is not a number is ignored. */
    static boolean isNewer(String remote, String local) {
        int[] a = nums(remote), b = nums(local);
        if (a.length == 0) return false;
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] nums(String s) {
        if (s == null) return new int[0];
        String[] parts = s.replaceAll("[^0-9.]", ".").split("\\.");
        int n = 0;
        int[] out = new int[parts.length];
        for (String p : parts) {
            if (p.isEmpty()) continue;
            try {
                out[n++] = Integer.parseInt(p.length() > 9 ? p.substring(0, 9) : p);
            } catch (NumberFormatException ignored) {
            }
        }
        int[] r = new int[n];
        System.arraycopy(out, 0, r, 0, n);
        return r;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "SimoMusic-updater");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        return c;
    }

    /** Runs on a background thread; the callback arrives on the main thread. */
    public static void check(final Context ctx, final CheckCb cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            Info info = null;
            int err = 0;
            HttpURLConnection c = null;
            try {
                c = open("https://api.github.com/repos/" + REPO + "/releases/latest");
                int code = c.getResponseCode();
                if (code == 404) {
                    err = E_NONE;
                } else if (code == 403 || code == 429) {
                    err = E_LIMIT;
                } else if (code != 200) {
                    err = E_NET;
                } else {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    try (InputStream in = c.getInputStream()) {
                        byte[] buf = new byte[8192];
                        int k;
                        while ((k = in.read(buf)) > 0) bo.write(buf, 0, k);
                    }
                    JSONObject o = new JSONObject(bo.toString("UTF-8"));
                    Info i = new Info();
                    i.tag = o.optString("tag_name", "");
                    i.name = o.optString("name", i.tag);
                    i.notes = o.optString("body", "");
                    JSONArray assets = o.optJSONArray("assets");
                    if (assets != null) {
                        for (int k = 0; k < assets.length(); k++) {
                            JSONObject a = assets.getJSONObject(k);
                            if (a.optString("name", "").toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                                i.url = a.optString("browser_download_url", "");
                                i.size = a.optLong("size", 0);
                                break;
                            }
                        }
                    }
                    if (i.url.isEmpty()) err = E_NONE;
                    else {
                        i.newer = isNewer(i.tag, current(app));
                        info = i;
                    }
                }
            } catch (Exception e) {
                CrashGuard.nonFatal("update check", e);
                err = E_NET;
            } finally {
                if (c != null) c.disconnect();
            }
            final Info fi = info;
            final int fe = err;
            MAIN.post(() -> cb.done(fi, fe));
        }, "update-check").start();
    }

    public static File file(Context c) {
        File d = new File(c.getCacheDir(), "updates");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return new File(d, "SimoMusic-update.apk");
    }

    /** Downloads the apk; set {@code cancel} to stop. Progress and the result arrive on the main thread. */
    public static void download(final Context ctx, final Info info, final AtomicBoolean cancel, final DownloadCb cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            File out = file(app);
            int err = 0;
            HttpURLConnection c = null;
            try {
                File[] old = out.getParentFile().listFiles();
                if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                    f.delete();
                c = open(info.url);
                if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
                long total = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : info.size;
                long done = 0;
                int last = -1;
                try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int k;
                    while ((k = in.read(buf)) > 0) {
                        if (cancel.get()) throw new java.io.IOException("cancelled");
                        fo.write(buf, 0, k);
                        done += k;
                        if (total > 0) {
                            final int pct = (int) Math.min(100, done * 100 / total);
                            if (pct != last) {
                                last = pct;
                                MAIN.post(() -> cb.progress(pct));
                            }
                        }
                    }
                }
                if (total > 0 && done != total) throw new java.io.IOException("incomplete download");
                if (!isThisApp(app, out)) err = E_FILE;
            } catch (Exception e) {
                if (!cancel.get()) CrashGuard.nonFatal("update download", e);
                err = cancel.get() ? -1 : E_NET;
            } finally {
                if (c != null) c.disconnect();
            }
            if (err != 0) //noinspection ResultOfMethodCallIgnored
                out.delete();
            final int fe = err;
            if (fe == -1) return;
            MAIN.post(() -> cb.done(fe == 0 ? out : null, fe));
        }, "update-download").start();
    }

    /** The file must be an apk of this very app, otherwise it is never offered to the installer. */
    private static boolean isThisApp(Context c, File f) {
        try {
            PackageInfo p = c.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), 0);
            return p != null && c.getPackageName().equals(p.packageName);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Android 8+: the user must allow "install unknown apps" for SimoMusic once. */
    public static boolean canInstall(Context c) {
        return Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    public static void askInstallPermission(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        Ui.go(c, new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName())));
    }

    /** Opens the system installer for the downloaded apk. Returns false when it could not be started. */
    public static boolean install(Context c, File apk) {
        try {
            Uri u = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(u, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            CrashGuard.nonFatal("install update", e);
            return false;
        }
    }
}
