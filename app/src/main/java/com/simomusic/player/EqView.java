package com.simomusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/** Three bouncing bars: the "this song is playing" mark drawn over the cover. Frames are only produced while animating. */
public final class EqView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] speed = {1.00f, 1.45f, 0.80f};
    private final float[] phase = {0f, 1.7f, 3.1f};
    private final float[] rest = {0.55f, 0.90f, 0.70f};
    private boolean animating;

    public EqView(Context c) {
        super(c);
        p.setColor(0xFFFFFFFF);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setAnimating(boolean a) {
        a = a && !PowerSaver.on(getContext());     // battery saver: still bars instead of motion
        if (a == animating) return;
        animating = a;
        invalidate();
    }

    @Override
    protected void onVisibilityChanged(View v, int vis) {
        super.onVisibilityChanged(v, vis);
        if (vis == VISIBLE && animating) invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), bar = w / 5f;
        p.setStrokeWidth(bar);
        long t = SystemClock.uptimeMillis();
        for (int i = 0; i < 3; i++) {
            float f = animating ? 0.30f + 0.70f * (0.5f + 0.5f * (float) Math.sin(t / 210f * speed[i] + phase[i])) : rest[i];
            float x = bar / 2f + i * 2f * bar;
            float top = Math.min(h - bar / 2f, h - h * f + bar / 2f);
            c.drawLine(x, h - bar / 2f, x, top, p);
        }
        if (animating) postInvalidateDelayed(33);   // ~30 fps is plenty for three small bars and halves the drawing work on 90/120 Hz screens
    }
}
