package com.simomusic.player;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

/**
 * Keeps the song list in step with the phone by itself: whenever Android's media database changes (a song was
 * downloaded, copied, recorded, renamed or deleted by any app) the library is re-read a moment later and every open
 * screen refreshes, so pulling down to refresh is never needed. Bursts of changes (a download writing a file) are
 * merged into one scan.
 */
public final class MediaWatcher {
    private MediaWatcher() {
    }

    private static final long DELAY_MS = 800;
    private static boolean started;
    private static ContentObserver observer;

    public static synchronized void start(Context c) {
        if (started) return;
        final Context app = c.getApplicationContext();
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable scan = () -> {
            if (Perms.hasAudio(app)) Library.scan(app, null);
        };
        observer = new ContentObserver(h) {
            @Override
            public void onChange(boolean selfChange) {
                h.removeCallbacks(scan);
                h.postDelayed(scan, DELAY_MS);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                onChange(selfChange);
            }
        };
        try {
            app.getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer);
            if (Build.VERSION.SDK_INT >= 29) {
                app.getContentResolver().registerContentObserver(
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), true, observer);
            }
            started = true;
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("media watcher", e);
        }
    }
}
