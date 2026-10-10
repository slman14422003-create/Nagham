package com.simomusic.player;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.Arrays;

/** Long-press the app icon: "Play all" and "Shuffle" start music straight from the launcher. */
public final class AppShortcuts {
    private AppShortcuts() {
    }

    public static final String ACTION_PLAY_ALL = "app.simomusic.player.action.PLAY_ALL";
    public static final String ACTION_SHUFFLE = "app.simomusic.player.action.SHUFFLE";

    public static boolean isShortcut(String action) {
        return ACTION_PLAY_ALL.equals(action) || ACTION_SHUFFLE.equals(action);
    }

    public static void install(Context ctx) {
        if (Build.VERSION.SDK_INT < 25) return;
        try {
            Context c = ctx.getApplicationContext();
            ShortcutManager sm = c.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            ShortcutInfo play = build(c, "play_all", R.string.play_all, R.drawable.ic_play_fill, ACTION_PLAY_ALL);
            ShortcutInfo shuffle = build(c, "shuffle", R.string.shuffle_all, R.drawable.ic_shuffle, ACTION_SHUFFLE);
            sm.setDynamicShortcuts(Arrays.asList(play, shuffle));
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("app shortcuts", e);
        }
    }

    private static ShortcutInfo build(Context c, String id, int label, int icon, String action) {
        Intent i = new Intent(c, MainActivity.class).setAction(action).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return new ShortcutInfo.Builder(c, id)
                .setShortLabel(c.getString(label))
                .setLongLabel(c.getString(label))
                .setIcon(Icon.createWithBitmap(badge(c, icon)))
                .setIntent(i)
                .build();
    }

    /** A white glyph on the accent colour in a circle: the plain black vectors would vanish on dark launchers. */
    private static Bitmap badge(Context c, int res) {
        int size = Ui.dp(c, 64);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Ui.color(c, R.color.accent));
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p);
        Drawable d = ContextCompat.getDrawable(c, res);
        if (d != null) {
            d = d.mutate();
            d.setTint(0xFFFFFFFF);
            int pad = size / 4;
            d.setBounds(pad, pad, size - pad, size - pad);
            d.draw(cv);
        }
        return b;
    }
}
