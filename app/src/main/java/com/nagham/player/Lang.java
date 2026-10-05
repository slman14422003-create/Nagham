package com.nagham.player;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** In-app language: follow the system, or force Arabic / English. AppCompat recreates screens and remembers it. */
public final class Lang {
    private Lang() {
    }

    /** "" = follow system, "ar" or "en". */
    public static String saved(Context c) {
        return Store.prefs(c).getString("lang", "");
    }

    public static void set(Context c, String v) {
        Store.prefs(c).edit().putString("lang", v).apply();
        AppCompatDelegate.setApplicationLocales("ar".equals(v) || "en".equals(v)
                ? LocaleListCompat.forLanguageTags(v) : LocaleListCompat.getEmptyLocaleList());
    }
}
