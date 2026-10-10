package com.simomusic.player;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Android 15+ (and every app targeting API 35/36) draws edge-to-edge and the old opt-out is gone on
 * Android 16. This keeps every screen clear of the status bar, navigation bar, cutout and keyboard.
 */
public class App extends Application {
    public static final String CH_LOCK = "lock_player";
    private static int visible;

    /** How many of the app's screens are on screen right now (0 = the app is in the background or was swiped away). */
    public static int visibleCount() {
        return visible;
    }

    private static final java.util.List<java.lang.ref.WeakReference<Activity>> LIVE = new java.util.ArrayList<>();

    /** Re-creates every open screen so a new accent shows everywhere, including the ones behind this one. */
    public static void recreateAll() {
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            Activity a = LIVE.get(i).get();
            if (a == null || a.isFinishing() || a.isDestroyed()) {
                LIVE.remove(i);
                continue;
            }
            try {
                a.recreate();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CrashGuard.install(this);
        Accent.applyMode(this);
        MemoryGuard.install(this);   // covers are released when Android is short of memory
        MediaWatcher.start(this);   // songs downloaded or deleted anywhere show up in the app by themselves
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH_LOCK, getString(R.string.ch_lock), NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription(getString(R.string.ch_lock_sub));
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity a) {
                visible++;
                if (Build.VERSION.SDK_INT < 35 || a instanceof FullBleed) return;
                View content = a.findViewById(android.R.id.content);
                if (content == null || content.getTag(R.id.tag_insets) != null) return;
                content.setTag(R.id.tag_insets, Boolean.TRUE);
                ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                    Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
                    v.setPadding(b.left, b.top, b.right, b.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });
                ViewCompat.requestApplyInsets(content);
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {
                // runs inside super.onCreate(), before the screen builds its views
                Accent.apply(a.getTheme(), a);
                Ui.highRefresh(a);
                LIVE.add(new java.lang.ref.WeakReference<>(a));
            }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) { if (visible > 0) visible--; }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override
            public void onActivityDestroyed(Activity a) {
                for (int i = LIVE.size() - 1; i >= 0; i--) {
                    Activity x = LIVE.get(i).get();
                    if (x == null || x == a) LIVE.remove(i);
                }
            }
        });
    }
}
