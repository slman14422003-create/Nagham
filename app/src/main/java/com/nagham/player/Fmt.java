package com.nagham.player;

import android.content.Context;

import java.util.Locale;

public final class Fmt {
    private Fmt() {
    }

    public static String time(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60;
        s %= 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }

    public static String artist(Context c, String a) {
        return a == null || a.trim().isEmpty() ? c.getString(R.string.unknown_artist) : a;
    }
}
