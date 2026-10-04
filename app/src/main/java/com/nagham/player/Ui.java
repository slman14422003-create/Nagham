package com.nagham.player;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.ImageViewCompat;

/** The same look as the GitHub Manager app: black canvas, graphite grouped cards, pill buttons, round icon buttons. */
public final class Ui {
    private Ui() {
    }

    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int color(Context c, int res) {
        return ContextCompat.getColor(c, res);
    }

    public static void press(Context c, View v) {
        v.setStateListAnimator(AnimatorInflater.loadStateListAnimator(c, R.animator.press_scale));
    }

    public static void round(View v, final int radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    public static void tint(ImageView v, int colorRes) {
        ImageViewCompat.setImageTintList(v, ColorStateList.valueOf(color(v.getContext(), colorRes)));
    }

    public static TextView text(Context c, CharSequence t, int sp, int colorRes) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color(c, colorRes));
        v.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return v;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    /** Round graphite icon button (the header buttons of the GitHub Manager app). */
    public static ImageButton icon(Context c, int res, int desc, View.OnClickListener l) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(res);
        b.setBackgroundResource(R.drawable.bg_icon_btn);
        b.setScaleType(ImageView.ScaleType.CENTER);
        tint(b, R.color.text_primary);
        b.setLayoutParams(lp(dp(c, 44), dp(c, 44)));
        if (desc != 0) b.setContentDescription(c.getString(desc));
        if (l != null) b.setOnClickListener(l);
        press(c, b);
        return b;
    }

    /** Borderless transport / action button. */
    public static ImageButton flat(Context c, int res, int sizeDp, int desc, View.OnClickListener l) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(res);
        b.setBackgroundResource(R.drawable.bg_ripple_round);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = dp(c, sizeDp / 4);
        b.setPadding(p, p, p, p);
        tint(b, R.color.text_primary);
        b.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        if (desc != 0) b.setContentDescription(c.getString(desc));
        if (l != null) b.setOnClickListener(l);
        press(c, b);
        return b;
    }

    /** The big white play / pause circle. */
    public static ImageButton bigPlay(Context c, View.OnClickListener l) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(R.drawable.ic_play_fill);
        b.setBackgroundResource(R.drawable.bg_play_circle);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = dp(c, 22);
        b.setPadding(p, p, p, p);
        tint(b, R.color.on_accent);
        b.setLayoutParams(lp(dp(c, 72), dp(c, 72)));
        b.setContentDescription(c.getString(R.string.play_pause));
        b.setOnClickListener(l);
        press(c, b);
        return b;
    }

    public static ImageView artView(Context c, int sizeDp, int radiusDp) {
        ImageView iv = new ImageView(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(c, R.color.surface_high));
        g.setCornerRadius(dp(c, radiusDp));
        iv.setBackground(g);
        round(iv, dp(c, radiusDp));
        iv.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    public static Button pill(Context c, int textRes, int iconRes, boolean primary, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(textRes);
        b.setAllCaps(false);
        b.setBackgroundResource(primary ? R.drawable.btn_primary : R.drawable.btn_secondary);
        int col = color(c, primary ? R.color.on_accent : R.color.text_primary);
        b.setTextColor(col);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(c, 46));
        b.setMinimumHeight(dp(c, 46));
        b.setPaddingRelative(dp(c, 18), 0, dp(c, 20), 0);
        if (iconRes != 0) {
            Drawable d = DrawableCompat.wrap(ContextCompat.getDrawable(c, iconRes).mutate());
            DrawableCompat.setTint(d, col);
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
            b.setCompoundDrawablePadding(dp(c, 8));
        }
        b.setOnClickListener(l);
        press(c, b);
        return b;
    }

    public static EditText edit(Context c, CharSequence hint, CharSequence text) {
        EditText e = new EditText(c);
        e.setHint(hint);
        if (text != null) e.setText(text);
        e.setSingleLine(true);
        e.setBackgroundResource(R.drawable.bg_input);
        e.setTextColor(color(c, R.color.text_primary));
        e.setHintTextColor(color(c, R.color.text_hint));
        e.setTextSize(15);
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        e.setMinHeight(dp(c, 52));
        e.setPaddingRelative(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        LinearLayout.LayoutParams lp = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        e.setLayoutParams(lp);
        return e;
    }

    public static TextView chip(Context c, CharSequence text, boolean selected) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(13);
        t.setSingleLine(true);
        t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        LinearLayout.LayoutParams lp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(c, 8));
        t.setLayoutParams(lp);
        setChip(c, t, selected);
        press(c, t);
        return t;
    }

    public static void setChip(Context c, TextView t, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 100));
        g.setStroke(dp(c, 1), color(c, selected ? R.color.accent : R.color.stroke));
        g.setColor(color(c, selected ? R.color.accent_soft : R.color.surface));
        t.setBackground(g);
        t.setTextColor(color(c, selected ? R.color.accent_text : R.color.text_secondary));
    }

    public static TextView section(Context c, int textRes) {
        TextView t = text(c, c.getString(textRes), 14, R.color.text_secondary);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPaddingRelative(dp(c, 26), dp(c, 22), dp(c, 26), dp(c, 8));
        return t;
    }

    /** Top bar: round back button, centered serif title, optional round action buttons. */
    public static LinearLayout topBar(final android.app.Activity a, CharSequence title, int backIcon, ImageButton... actions) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 8));
        bar.addView(icon(a, backIcon, R.string.back, v -> a.finish()));
        TextView t = text(a, title, 20, R.color.text_primary);
        t.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = weight(1);
        lp.setMargins(dp(a, 8), 0, dp(a, 8), 0);
        bar.addView(t, lp);
        if (actions.length == 0) {
            View sp = new View(a);
            bar.addView(sp, lp(dp(a, 44), dp(a, 44)));
        }
        for (ImageButton b : actions) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) b.getLayoutParams();
            p.setMarginStart(dp(a, 6));
            bar.addView(b);
        }
        return bar;
    }

    // ------------------------------------------------------------------ grouped rows

    /** Rounds a card like the ChatGPT-style grouped lists: big corners at the group ends, small in between. */
    public static void shape(Context c, View card, boolean first, boolean last, int fillRes) {
        float big = dp(c, 24), small = dp(c, 6);
        float top = first ? big : small, bottom = last ? big : small;
        float[] r = {top, top, top, top, bottom, bottom, bottom, bottom};
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(color(c, fillRes));
        fill.setCornerRadii(r);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadii(r);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(color(c, R.color.ripple)), fill, mask));
    }

    /** Shapes every consecutive run of row cards inside a container as one rounded group. */
    public static void group(Context c, ViewGroup g) {
        int n = g.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = g.getChildAt(i);
            if (v.findViewById(R.id.card) == null) continue;
            boolean first = i == 0 || g.getChildAt(i - 1).findViewById(R.id.card) == null;
            boolean last = i == n - 1 || g.getChildAt(i + 1).findViewById(R.id.card) == null;
            shape(c, v.findViewById(R.id.card), first, last, R.color.surface);
        }
    }

    private static FrameLayout rowShell(Context c, LinearLayout card) {
        FrameLayout w = new FrameLayout(c);
        w.setPadding(dp(c, 14), dp(c, 1), dp(c, 14), dp(c, 1));
        card.setId(R.id.card);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(c, 64));
        card.setPaddingRelative(dp(c, 18), dp(c, 12), dp(c, 16), dp(c, 12));
        card.setDuplicateParentStateEnabled(true);
        w.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return w;
    }

    private static LinearLayout textColumn(Context c, CharSequence title, CharSequence sub) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(c, title, 16, R.color.text_primary));
        if (sub != null) {
            TextView s = text(c, sub, 13, R.color.text_secondary);
            s.setPadding(0, dp(c, 3), 0, 0);
            col.addView(s);
        }
        return col;
    }

    /** A settings row; trailing is an optional pill (e.g. "Enabled"), icon an optional leading icon. */
    public static View settingRow(Context c, int iconRes, CharSequence title, CharSequence sub,
                                  CharSequence trailing, boolean trailingOk, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(c);
        FrameLayout w = rowShell(c, card);
        if (iconRes != 0) {
            ImageView i = new ImageView(c);
            i.setImageResource(iconRes);
            tint(i, R.color.text_secondary);
            card.addView(i, lp(dp(c, 24), dp(c, 24)));
        }
        LinearLayout.LayoutParams lp = weight(1);
        lp.setMarginStart(dp(c, iconRes != 0 ? 16 : 0));
        lp.setMarginEnd(dp(c, 10));
        card.addView(textColumn(c, title, sub), lp);
        if (trailing != null) {
            TextView b = text(c, trailing, 12, trailingOk ? R.color.ok : R.color.accent_text);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setPadding(dp(c, 12), dp(c, 5), dp(c, 12), dp(c, 5));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(c, 100));
            g.setColor(color(c, trailingOk ? R.color.neutral_soft : R.color.accent_soft));
            b.setBackground(g);
            card.addView(b);
        } else if (click != null) {
            ImageView ch = new ImageView(c);
            ch.setImageResource(R.drawable.ic_chevron);
            tint(ch, R.color.text_hint);
            card.addView(ch, lp(dp(c, 20), dp(c, 20)));
        }
        if (click != null) w.setOnClickListener(click);
        return w;
    }

    public static View toggleRow(Context c, CharSequence title, CharSequence sub, boolean on,
                                 final CompoundButton.OnCheckedChangeListener l) {
        LinearLayout card = new LinearLayout(c);
        FrameLayout w = rowShell(c, card);
        LinearLayout.LayoutParams lp = weight(1);
        lp.setMarginEnd(dp(c, 14));
        card.addView(textColumn(c, title, sub), lp);
        final SwitchCompat sw = new SwitchCompat(c);
        sw.setBackground(null);
        sw.setShowText(false);
        sw.setThumbDrawable(ContextCompat.getDrawable(c, R.drawable.switch_thumb));
        sw.setTrackDrawable(ContextCompat.getDrawable(c, R.drawable.switch_track));
        sw.setChecked(on);
        sw.setClickable(false);
        sw.setFocusable(false);
        card.addView(sw);
        w.setOnClickListener(v -> {
            sw.toggle();
            l.onCheckedChanged(sw, sw.isChecked());
        });
        return w;
    }
}
