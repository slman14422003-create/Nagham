package com.simomusic.player;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Brings the lock-screen player above the keyguard. Since Android 10 an app cannot start an activity from the
 * background, so the one sanctioned route is a full-screen-intent notification (the same one alarms use).
 */
public final class LockLauncher {
    private LockLauncher() {
    }

    public static final int ID = 4401;
    private static final Handler H = new Handler(Looper.getMainLooper());

    /** elapsedRealtime when the lock player was last visible, so pressing power on it doesn't instantly re-open it. */
    public static volatile long lastSeen;

    /** True when the notification and "show over lock screen" access that the player needs are both granted. */
    public static boolean ready(Context c) {
        return Perms.hasNotif(c) && Perms.hasFsi(c) && Perms.hasLockChannel(c);
    }

    /**
     * Two routes, both attempted: a direct start (allowed when Android permits background launches: Xiaomi "pop-up
     * windows in background", or "display over other apps"), and a full-screen-intent notification (the standard
     * route that also works on Samsung once "full screen notifications" is granted).
     */
    public static boolean show(Context ctx) {
        Context c = ctx.getApplicationContext();
        Intent i = new Intent(c, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        boolean started = false;
        try {
            c.startActivity(i);
            started = true;
        } catch (Exception ignored) {
        }
        if (!Perms.hasNotif(c) || !Perms.hasFsi(c)) return started;
        PendingIntent pi = PendingIntent.getActivity(c, 11, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(c, App.CH_LOCK)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(c.getString(R.string.app_name))
                .setContentText(c.getString(R.string.lock_notif_text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setTimeoutAfter(8000)
                .build();
        try {
            NotificationManagerCompat.from(c).notify(ID, n);
            return true;
        } catch (RuntimeException e) {
            return started;
        }
    }

    public static void clear(Context c) {
        try {
            NotificationManagerCompat.from(c.getApplicationContext()).cancel(ID);
        } catch (RuntimeException ignored) {
        }
    }

    /** Settings → "Test": lock the phone, and the player should appear after the delay. */
    public static void testIn(final Context c, int seconds) {
        H.postDelayed(() -> show(c), seconds * 1000L);
    }
}
