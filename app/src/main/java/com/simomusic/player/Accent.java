package com.simomusic.player;

import android.content.Context;
import android.content.res.Resources;

import androidx.appcompat.app.AppCompatDelegate;

/** Theme mode (auto / light / dark) and the accent color, both chosen in Settings and applied app-wide. */
public final class Accent {
    private Accent() {
    }

    public static final int[] STYLES = {
        R.style.AccentBlue,
        R.style.AccentViolet,
        R.style.AccentPink,
        R.style.AccentRed,
        R.style.AccentOrange,
        R.style.AccentGreen,
        R.style.AccentTeal,
        R.style.AccentGold,
        R.style.AccentSystem     // Android 12+: follows the wallpaper palette chosen in One UI (Settings > Wallpaper and style)
    };

    /** Swatch colors shown in Settings: {light variant, dark variant}. */
    public static final int[][] SWATCH = {
        {0xFF2F5BE8, 0xFF3D6BFF},
        {0xFF7C3AED, 0xFF7C5CFF},
        {0xFFD81B76, 0xFFE5338C},
        {0xFFD92D2D, 0xFFE5383B},
        {0xFFD9560B, 0xFFFF8A3D},
        {0xFF15803D, 0xFF34D399},
        {0xFF0F766E, 0xFF2DD4BF},
        {0xFFA16207, 0xFFFACC15},
        {0xFF2F5BE8, 0xFF3D6BFF}   // placeholder: the real swatch of the system accent comes from swatch()
    };

    /** The system accent needs Android 12 (Material You palette); older phones only get the eight fixed ones. */
    public static final int SYSTEM = 8;

    public static int count() {
        return android.os.Build.VERSION.SDK_INT >= 31 ? STYLES.length : STYLES.length - 1;
    }

    /** Color of a swatch dot in Settings. */
    public static int swatch(Context c, int i, boolean night) {
        if (i == SYSTEM && android.os.Build.VERSION.SDK_INT >= 31) {
            return c.getColor(night ? android.R.color.system_accent1_400 : android.R.color.system_accent1_600);
        }
        return SWATCH[i][night ? 1 : 0];
    }

    public static int index(Context c) {
        // nothing chosen yet: on Android 12+ start with the phone's own palette so the app matches One UI from the first launch
        int def = android.os.Build.VERSION.SDK_INT >= 31 ? SYSTEM : 0;
        return Math.max(0, Math.min(count() - 1, Store.prefs(c).getInt("accent", def)));
    }

    public static void set(Context c, int i) {
        Store.prefs(c).edit().putInt("accent", i).apply();
    }

    /** Layers the chosen accent over an activity / context theme. */
    public static void apply(Resources.Theme t, Context c) {
        t.applyStyle(STYLES[index(c)], true);
    }

    public static int mode(Context c) {
        return Store.prefs(c).getInt("theme_mode", 0);
    }

    public static void setMode(Context c, int m) {
        Store.prefs(c).edit().putInt("theme_mode", m).apply();
        applyMode(c);
    }

    public static void applyMode(Context c) {
        int m = mode(c);
        AppCompatDelegate.setDefaultNightMode(m == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : m == 2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
