package com.simomusic.player;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * Knows when the phone is in battery-saver mode, so the app can drop its purely decorative motion (the bouncing
 * equalizer bars, extra animations) and ask the screen for fewer frames. The answer is cached for a few seconds,
 * so asking on every draw is free.
 */
public final class PowerSaver {
    private PowerSaver() {
    }

    private static long checkedAt = -10000;
    private static boolean cached;

    public static boolean on(Context c) {
        long now = SystemClock.elapsedRealtime();
        if (now - checkedAt < 5000) return cached;
        checkedAt = now;
        boolean v = false;
        try {
            PowerManager pm = (PowerManager) c.getApplicationContext().getSystemService(Context.POWER_SERVICE);
            v = pm != null && pm.isPowerSaveMode();
        } catch (RuntimeException ignored) {
        }
        cached = v;
        return v;
    }
}
