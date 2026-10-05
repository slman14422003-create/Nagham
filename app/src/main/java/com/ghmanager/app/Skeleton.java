package com.ghmanager.app;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

/** Grey placeholder cards that pulse while a list loads (the screen keeps its shape instead of showing "loading"). */
public final class Skeleton {
    private Skeleton() {
    }

    private static View block(Context c, int wDp, int hDp, int radiusDp) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.color(c, R.color.neutral_soft));
        g.setCornerRadius(Ui.dp(c, radiusDp));
        v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                wDp <= 0 ? ViewGroup.LayoutParams.MATCH_PARENT : Ui.dp(c, wDp), Ui.dp(c, hDp));
        v.setLayoutParams(lp);
        return v;
    }

    public static View build(Context c, int rows) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), 0);
        col.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int[] widths = {170, 130, 200, 150, 110, 185};
        for (int i = 0; i < rows; i++) {
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cl.bottomMargin = Ui.dp(c, 10);
            card.setLayoutParams(cl);

            View icon = block(c, 36, 36, 12);
            card.addView(icon);

            LinearLayout text = new LinearLayout(c);
            text.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.setMarginStart(Ui.dp(c, 14));
            card.addView(text, tl);
            View t1 = block(c, widths[i % widths.length], 14, 7);
            View t2 = block(c, widths[(i + 3) % widths.length] - 30, 11, 6);
            ((LinearLayout.LayoutParams) t2.getLayoutParams()).topMargin = Ui.dp(c, 9);
            text.addView(t1);
            text.addView(t2);
            col.addView(card);
        }
        return col;
    }

    /** Starts / stops the soft pulse. */
    public static void pulse(View v, boolean on) {
        Object old = v.getTag(R.id.tag_spin);
        if (old instanceof ObjectAnimator) ((ObjectAnimator) old).cancel();
        v.setAlpha(1f);
        if (!on || !android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ALPHA, 1f, 0.45f);
        a.setDuration(750);
        a.setRepeatMode(ObjectAnimator.REVERSE);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        v.setTag(R.id.tag_spin, a);
        a.start();
    }
}
