package com.nagham.player;

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

    /** True when the notification and "show over lock screen" access that the player needs are both granted. */
    public static boolean ready(Context c) {
        return Perms.hasNotif(c) && Perms.hasFsi(c);
    }

    /** @return false when notifications or the "show over lock screen" access are missing. */
    public static boolean show(Context ctx) {
        Context c = ctx.getApplicationContext();
        if (!Perms.hasNotif(c) || !Perms.hasFsi(c)) return false;
        Intent i = new Intent(c, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
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
        } catch (SecurityException e) {
            return false;
        }
    }

    public static void clear(Context c) {
        NotificationManagerCompat.from(c.getApplicationContext()).cancel(ID);
    }

    /** Settings → "Test": lock the phone, and the player should appear after the delay. */
    public static void testIn(final Context c, int seconds) {
        H.postDelayed(() -> show(c), seconds * 1000L);
    }
}
