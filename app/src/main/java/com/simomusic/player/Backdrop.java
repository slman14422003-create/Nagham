package com.simomusic.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.widget.ImageView;

/** Soft, colorful full-screen backdrop made from the cover (tiny bitmap scaled up = free blur, works on every Android). */
public final class Backdrop {
    private Backdrop() {
    }

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    public static ImageView view(Context c) {
        ImageView bg = new ImageView(c);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.45f);
        bg.setColorFilter(new ColorMatrixColorFilter(cm));
        bg.setAlpha(0f);
        return bg;
    }

    static Bitmap soft(Bitmap src) {
        Bitmap b = src;
        while (b.getWidth() / 2 >= 24 && b.getHeight() / 2 >= 24) {
            b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
        }
        return b;
    }

    /** Shows the cover of the given track as backdrop (cross-fades); null hides it so the default gradient shows. */
    public static void set(Context c, final ImageView bg, final Uri u) {
        final String key = String.valueOf(u);
        bg.setTag(R.id.tag_art, key);
        if (u == null) {
            bg.animate().alpha(0f).setDuration(250).start();
            return;
        }
        Art.fetch(c, u, 256, bmp -> {
            if (!key.equals(bg.getTag(R.id.tag_art))) return;
            if (bmp == null) {
                bg.animate().alpha(0f).setDuration(250).start();
                return;
            }
            // several bitmap allocations to halve 256px down to ~24px; cheap on a background thread, but doing
            // it on the UI thread right as the hero entrance animation starts was a real source of the jank
            Art.EX.execute(() -> {
                final Bitmap small = soft(bmp);
                MAIN.post(() -> {
                    if (!key.equals(bg.getTag(R.id.tag_art))) return;
                    bg.setImageBitmap(small);
                    bg.setAlpha(0f);
                    bg.animate().alpha(1f).setDuration(500).start();
                });
            });
        });
    }
}
