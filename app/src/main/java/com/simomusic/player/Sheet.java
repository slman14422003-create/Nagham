package com.simomusic.player;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Bottom sheet: tappable rows with round icon badges, section dividers, a red "danger" row and pill choosers for
 * short lists (speed, timer, sort). Rows marked keepOpen refresh the list in place (fast multi-toggle).
 */
public final class Sheet {
    private Sheet() {
    }

    private static final int ROW = 0, DIVIDER = 1, CHIPS = 2;

    public static final class Item {
        final int kind, icon, selected, columns;
        final CharSequence text;
        final CharSequence[] labels;
        final boolean checked, keepOpen, danger;
        final Runnable run;
        final IntConsumer pick;

        Item(int kind, int icon, CharSequence text, boolean checked, boolean keepOpen, boolean danger, Runnable run,
             CharSequence[] labels, int selected, int columns, IntConsumer pick) {
            this.kind = kind;
            this.icon = icon;
            this.text = text;
            this.checked = checked;
            this.keepOpen = keepOpen;
            this.danger = danger;
            this.run = run;
            this.labels = labels;
            this.selected = selected;
            this.columns = columns;
            this.pick = pick;
        }
    }

    public static Item item(int icon, CharSequence text, boolean checked, boolean keepOpen, Runnable run) {
        return new Item(ROW, icon, text, checked, keepOpen, false, run, null, -1, 0, null);
    }

    /** A destructive action (delete): red icon and text, so it never looks like the rows around it. */
    public static Item danger(int icon, CharSequence text, Runnable run) {
        return new Item(ROW, icon, text, false, false, true, run, null, -1, 0, null);
    }

    /** A thin line that separates groups of rows. */
    public static Item divider() {
        return new Item(DIVIDER, 0, null, false, false, false, null, null, -1, 0, null);
    }

    /** Short list shown as pills in a grid ({@code columns} per row); the chosen one is highlighted, a tap picks and closes. */
    public static Item chips(CharSequence[] labels, int selected, int columns, IntConsumer pick) {
        return new Item(CHIPS, 0, null, false, false, false, null, labels, selected, Math.max(1, columns), pick);
    }

    public static void show(final Context c, CharSequence title, final Supplier<List<Item>> source) {
        show(c, title, null, source);
    }

    public static void show(final Context c, CharSequence title, CharSequence subtitle, final Supplier<List<Item>> source) {
        if (Dlg.dead(c)) return;
        final Dialog d = new Dialog(c, R.style.AppSheet);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_sheet);
        root.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 24));

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        View grab = new View(c);
        grab.setBackgroundResource(R.drawable.bg_grabber);
        LinearLayout.LayoutParams gp = Ui.lp(Ui.dp(c, 40), Ui.dp(c, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = Ui.dp(c, 4);
        gp.bottomMargin = Ui.dp(c, 8);
        head.addView(grab, gp);

        TextView t = Ui.text(c, title, 17, R.color.text_primary);
        t.setTypeface(Typeface.create("serif", Typeface.BOLD));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        boolean hasSub = subtitle != null && subtitle.length() > 0;
        t.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, hasSub ? 0 : 10));
        head.addView(t);
        if (hasSub) {
            TextView s = Ui.text(c, subtitle, 13, R.color.text_secondary);
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 2), Ui.dp(c, 24), Ui.dp(c, 12));
            head.addView(s);
        }
        root.addView(head);
        Ui.dragDismiss(head, root, d);

        final int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.62f);
        ScrollView sv = new ScrollView(c) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(c, 6), 0, 0);
        sv.addView(list);
        root.addView(sv);

        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            list.removeAllViews();
            for (final Item it : source.get()) {
                if (it.kind == DIVIDER) list.addView(thinLine(c));
                else if (it.kind == CHIPS) list.addView(chipsView(c, it, d));
                else list.addView(row(c, it, d, fill[0]));
            }
        };
        fill[0].run();

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        Dlg.bind(c, d, null);
        d.show();
    }

    private static View thinLine(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Ui.color(c, R.color.stroke));
        LinearLayout.LayoutParams lp = Ui.lp(-1, 1);
        lp.setMargins(Ui.dp(c, 24), Ui.dp(c, 6), Ui.dp(c, 24), Ui.dp(c, 6));
        v.setLayoutParams(lp);
        return v;
    }

    private static View chipsView(Context c, final Item it, final Dialog d) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(Ui.dp(c, 19), Ui.dp(c, 4), Ui.dp(c, 19), Ui.dp(c, 4));
        LinearLayout row = null;
        for (int i = 0; i < it.labels.length; i++) {
            if (i % it.columns == 0) {
                row = new LinearLayout(c);
                box.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            final int idx = i;
            boolean on = i == it.selected;
            TextView t = new TextView(c);
            t.setText(it.labels[i]);
            t.setTextSize(15);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
            t.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_off);
            t.setTextColor(Ui.color(c, on ? R.color.on_accent : R.color.text_primary));
            t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            Ui.press(c, t);
            t.setOnClickListener(v -> {
                Ui.tap(v);
                d.dismiss();
                if (it.pick != null) it.pick.accept(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 46), 1f);
            lp.setMargins(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5));
            row.addView(t, lp);
        }
        // an unfinished last row keeps the same pill width as the others
        if (row != null) {
            for (int k = it.labels.length % it.columns; k != 0 && k < it.columns; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 46), 1f);
                lp.setMargins(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5));
                row.addView(new View(c), lp);
            }
        }
        return box;
    }

    private static View row(Context c, final Item it, final Dialog d, final Runnable refill) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(c, 58));
        r.setPaddingRelative(Ui.dp(c, 20), Ui.dp(c, 4), Ui.dp(c, 24), Ui.dp(c, 4));
        r.setBackgroundResource(R.drawable.bg_ripple_rect);

        int tintRes = it.danger ? R.color.bad : it.checked ? R.color.accent_text : R.color.text_secondary;
        int textRes = it.danger ? R.color.bad : it.checked ? R.color.accent_text : R.color.text_primary;

        // round badge behind the icon: soft accent when selected, soft red for delete, neutral otherwise
        FrameLayout badge = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (it.danger) g.setColor((Ui.color(c, R.color.bad) & 0x00FFFFFF) | 0x26000000);
        else g.setColor(Ui.color(c, it.checked ? R.color.accent_soft : R.color.surface_high));
        badge.setBackground(g);
        ImageView i = new ImageView(c);
        i.setImageResource(it.icon);
        Ui.tint(i, tintRes);
        badge.addView(i, new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER));
        r.addView(badge, Ui.lp(Ui.dp(c, 40), Ui.dp(c, 40)));

        TextView tv = Ui.text(c, it.text, 16, textRes);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (it.checked) tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = Ui.weight(1);
        lp.setMarginStart(Ui.dp(c, 16));
        r.addView(tv, lp);
        if (it.checked) {
            ImageView ck = new ImageView(c);
            ck.setImageResource(R.drawable.ic_check);
            Ui.tint(ck, R.color.accent_text);
            r.addView(ck, Ui.lp(Ui.dp(c, 22), Ui.dp(c, 22)));
        }
        r.setOnClickListener(v -> {
            if (it.run != null) it.run.run();
            if (it.keepOpen) refill.run();
            else d.dismiss();
        });
        return r;
    }
}
