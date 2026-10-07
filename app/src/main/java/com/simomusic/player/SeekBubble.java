package com.simomusic.player;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

/** The little "+10s" / "-10s" pill that pops on the cover after a double tap. Lives in the view's overlay. */
final class SeekBubble extends Drawable {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String text;
    private int alpha = 255;

    private SeekBubble(String text, float textPx) {
        this.text = text;
        bg.setColor(0xCC000000);
        tx.setColor(0xFFFFFFFF);
        tx.setTextSize(textPx);
        tx.setTypeface(Typeface.DEFAULT_BOLD);
        tx.setTextAlign(Paint.Align.CENTER);
    }

    /** side: -1 left half, +1 right half of the view. */
    static void show(final View v, int side, String text) {
        float tp = Ui.dp(v.getContext(), 18);
        final SeekBubble b = new SeekBubble(text, tp);
        int w = v.getWidth(), h = v.getHeight();
        int bw = (int) (tp * 5), bh = (int) (tp * 2.4f);
        int cx = side < 0 ? w / 4 : w * 3 / 4, cy = h / 2;
        b.setBounds(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);
        v.getOverlay().add(b);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(750);
        a.addUpdateListener(x -> {
            float t = (Float) x.getAnimatedValue();
            b.alpha = t < 0.15f ? (int) (255 * t / 0.15f) : (t > 0.6f ? (int) (255 * (1f - (t - 0.6f) / 0.4f)) : 255);
            b.invalidateSelf();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator an) {
                try {
                    v.getOverlay().remove(b);
                } catch (Exception ignored) {
                }
            }
        });
        a.start();
    }

    @Override
    public void draw(Canvas c) {
        bg.setAlpha(alpha * 204 / 255);
        tx.setAlpha(alpha);
        RectF r = new RectF(getBounds());
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, bg);
        c.drawText(text, r.centerX(), r.centerY() - (tx.ascent() + tx.descent()) / 2f, tx);
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
    }

    @Override
    public void setColorFilter(ColorFilter f) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
