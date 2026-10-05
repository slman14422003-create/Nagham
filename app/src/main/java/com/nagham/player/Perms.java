package com.nagham.player;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** Every permission the player needs to work smoothly up to Android 16. */
public final class Perms {
    public static final int REQ = 7001;

    private Perms() {
    }

    /** Android 13+ uses READ_MEDIA_AUDIO; older versions use the classic storage permission. */
    public static String audioPerm() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private static boolean granted(Context c, String p) {
        return ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasAudio(Context c) {
        return granted(c, audioPerm());
    }

    /** Covers both the Android 13+ runtime permission and the per-app switch in system settings. */
    public static boolean hasNotif(Context c) {
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    /** Android 14+: showing the lock-screen player over the keyguard needs this special access. */
    public static boolean hasFsi(Context c) {
        if (Build.VERSION.SDK_INT >= 34) {
            return ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).canUseFullScreenIntent();
        }
        return true;
    }

    /** The lock-screen category must stay "urgent / pop on screen", otherwise the system never launches it over the lock. */
    public static boolean hasLockChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return true;
        android.app.NotificationChannel ch = ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
                .getNotificationChannel(App.CH_LOCK);
        return ch == null || ch.getImportance() >= NotificationManager.IMPORTANCE_HIGH;
    }

    public static void askLockChannel(Activity a) {
        Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, App.CH_LOCK);
        start(a, i);
    }

    /** "Display over other apps": exempts the app from Android's background-launch block (see OverlayAnchor). */
    public static boolean hasOverlay(Context c) {
        return Settings.canDrawOverlays(c);
    }

    public static void askOverlay(Activity a) {
        start(a, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName())));
    }

    public static boolean hasBattery(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    public static boolean hasBt(Context c) {
        return Build.VERSION.SDK_INT < 31 || granted(c, Manifest.permission.BLUETOOTH_CONNECT);
    }

    public static boolean anyMissing(Context c) {
        return !hasAudio(c) || !hasNotif(c) || !hasFsi(c) || !hasLockChannel(c) || !hasOverlay(c) || !hasBt(c);
    }

    /** Runtime permissions asked together on first launch. */
    public static String[] firstRun() {
        List<String> l = new ArrayList<>();
        l.add(audioPerm());
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= 31) l.add(Manifest.permission.BLUETOOTH_CONNECT);
        return l.toArray(new String[0]);
    }

    /** Asks for a runtime permission; if the user already refused for good, opens the right settings page. */
    public static void request(Activity a, String perm, Intent fallback) {
        String key = "asked_" + perm;
        boolean asked = Store.flag(a, key, false);
        if (asked && !ActivityCompat.shouldShowRequestPermissionRationale(a, perm)) {
            start(a, fallback);
            return;
        }
        Store.setFlag(a, key, true);
        ActivityCompat.requestPermissions(a, new String[]{perm}, REQ);
    }

    public static void askAudio(Activity a) {
        request(a, audioPerm(), appSettings(a));
    }

    public static void askNotif(Activity a) {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName());
        if (Build.VERSION.SDK_INT >= 33 && !hasNotif(a)) request(a, Manifest.permission.POST_NOTIFICATIONS, i);
        else start(a, i);
    }

    public static void askBt(Activity a) {
        if (Build.VERSION.SDK_INT >= 31) request(a, Manifest.permission.BLUETOOTH_CONNECT, appSettings(a));
    }

    public static void askFsi(Activity a) {
        if (Build.VERSION.SDK_INT >= 34) {
            start(a, new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + a.getPackageName())));
        }
    }

    public static void askBattery(Activity a) {
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + a.getPackageName()));
        try {
            a.startActivity(i);
        } catch (Exception e) {
            start(a, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    public static void openApp(Activity a) {
        start(a, appSettings(a));
    }

    private static Intent appSettings(Activity a) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName()));
    }

    private static void start(Activity a, Intent i) {
        try {
            a.startActivity(i);
        } catch (Exception e) {
            try {
                a.startActivity(appSettings(a));
            } catch (Exception ignored) {
            }
        }
    }
}
