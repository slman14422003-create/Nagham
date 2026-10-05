package com.nagham.player;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * A 1-pixel, invisible, untouchable overlay window kept alive while music plays. Android lets an app that has
 * "display over other apps" start an activity from the background, but since Android 15 only while it actually has an
 * overlay window on screen: this is that window. It never draws anything and never receives touches.
 */
public final class OverlayAnchor {
    private OverlayAnchor() {
    }

    private static View view;

    public static void sync(Context ctx, boolean want) {
        Context c = ctx.getApplicationContext();
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        if (want && Settings.canDrawOverlays(c)) {
            if (view != null) return;
            View v = new View(c);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            try {
                wm.addView(v, lp);
                view = v;
            } catch (Exception ignored) {
            }
        } else if (view != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
            }
            view = null;
        }
    }
}
