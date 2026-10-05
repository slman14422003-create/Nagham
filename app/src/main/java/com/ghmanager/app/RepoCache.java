package com.ghmanager.app;

import android.content.Context;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The last repository list of the active account, kept in the app's private folder so the home screen can
 * show it at once while the fresh list is still downloading. Removed whenever an account signs out.
 */
public final class RepoCache {
    private RepoCache() {
    }

    private static File file(Context c) {
        Accounts.Acc a = Accounts.active(c);
        String id = a == null ? "none" : Integer.toHexString(a.id.hashCode());
        File dir = new File(c.getFilesDir(), "rc");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "repos_" + id + ".json");
    }

    public static JSONArray load(Context c) {
        try {
            File f = file(c);
            if (!f.exists() || f.length() == 0 || f.length() > 8 * 1024 * 1024) return null;
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            }
            return new JSONArray(new String(b, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    public static void save(Context c, JSONArray a) {
        try {
            File f = file(c);
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(a.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
        } catch (Exception ignored) {
        }
    }

    public static void clearAll(Context c) {
        File dir = new File(c.getFilesDir(), "rc");
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        GitHubApi.clearCache();
    }
}
