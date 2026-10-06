package com.nagham.player;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;

/**
 * Xiaomi (MIUI / HyperOS) and Samsung (One UI) add their own switches on top of Android's permissions.
 * These open the right vendor screens, falling back to the app's system settings page.
 */
public final class Oem {
    private Oem() {
    }

    private static String maker() {
        return (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase();
    }

    public static boolean xiaomi() {
        String m = maker();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    public static boolean samsung() {
        return maker().contains("samsung");
    }

    private static boolean tryStart(Activity a, Intent i) {
        try {
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** "Show on lock screen", "Display pop-up windows while running in the background", ... */
    public static void xiaomiPermissions(Activity a) {
        Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR");
        i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
        i.putExtra("extra_pkgname", a.getPackageName());
        if (tryStart(a, i)) return;
        i = new Intent("miui.intent.action.APP_PERM_EDITOR");
        i.setPackage("com.miui.securitycenter");
        i.putExtra("extra_pkgname", a.getPackageName());
        if (tryStart(a, i)) return;
        Perms.openApp(a);
    }

    public static void xiaomiAutostart(Activity a) {
        Intent i = new Intent();
        i.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        if (!tryStart(a, i)) Perms.openApp(a);
    }

    /** Xiaomi "No restrictions" / Samsung "Unrestricted, never sleeping" battery screens. */
    public static void battery(Activity a) {
        if (xiaomi()) {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
            i.putExtra("package_name", a.getPackageName());
            i.putExtra("package_label", a.getString(R.string.app_name));
            if (tryStart(a, i)) return;
        } else if (samsung()) {
            String[][] c = {{"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"},
                    {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
                    {"com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity"}};
            for (String[] x : c) {
                Intent i = new Intent();
                i.setComponent(new ComponentName(x[0], x[1]));
                if (tryStart(a, i)) return;
            }
        }
        Perms.askBattery(a);
    }
}
