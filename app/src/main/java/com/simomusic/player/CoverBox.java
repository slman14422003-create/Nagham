package com.simomusic.player;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Holds the cover and makes it the biggest square that fits the space left on the screen (capped), so the
 * player and the lock screen adapt to any phone: small, tall, punch-hole, foldable, split-screen.
 */
public final class CoverBox extends FrameLayout {
    private final int maxPx;

    public CoverBox(Context c, int maxDp) {
        super(c);
        maxPx = Ui.dp(c, maxDp);
    }

    public void setCover(View v) {
        v.setLayoutParams(new LayoutParams(-2, -2, Gravity.CENTER));
        addView(v);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        super.onMeasure(wSpec, hSpec);
        int w = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        int h = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        int s = Math.max(0, Math.min(Math.min(w, h), maxPx));
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY));
        }
    }
}
