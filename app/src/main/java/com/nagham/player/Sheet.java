package com.nagham.player;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.function.Supplier;

/** Bottom sheet with tappable rows. Rows marked keepOpen refresh the list in place (fast multi-toggle). */
public final class Sheet {
    private Sheet() {
    }

    public static final class Item {
        final int icon;
        final CharSequence text;
        final boolean checked, keepOpen;
        final Runnable run;

        Item(int icon, CharSequence text, boolean checked, boolean keepOpen, Runnable run) {
            this.icon = icon;
            this.text = text;
            this.checked = checked;
            this.keepOpen = keepOpen;
            this.run = run;
        }
    }

    public static Item item(int icon, CharSequence text, boolean checked, boolean keepOpen, Runnable run) {
        return new Item(icon, text, checked, keepOpen, run);
    }

    public static void show(final Context c, CharSequence title, final Supplier<List<Item>> source) {
        final Dialog d = new Dialog(c, R.style.AppSheet);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_sheet);
        root.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 28));

        View grab = new View(c);
        grab.setBackgroundResource(R.drawable.bg_grabber);
        LinearLayout.LayoutParams gp = Ui.lp(Ui.dp(c, 40), Ui.dp(c, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = Ui.dp(c, 8);
        root.addView(grab, gp);

        TextView t = Ui.text(c, title, 17, R.color.text_primary);
        t.setTypeface(Typeface.create("serif", Typeface.BOLD));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 10));
        root.addView(t);

        final int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.62f);
        ScrollView sv = new ScrollView(c) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv);

        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            list.removeAllViews();
            for (final Item it : source.get()) list.addView(row(c, it, d, fill[0]));
        };
        fill[0].run();

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }

    private static View row(Context c, final Item it, final Dialog d, final Runnable refill) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(c, 56));
        r.setPaddingRelative(Ui.dp(c, 24), 0, Ui.dp(c, 24), 0);
        r.setBackgroundResource(R.drawable.bg_ripple_rect);
        ImageView i = new ImageView(c);
        i.setImageResource(it.icon);
        Ui.tint(i, it.checked ? R.color.accent_text : R.color.text_secondary);
        r.addView(i, Ui.lp(Ui.dp(c, 24), Ui.dp(c, 24)));
        TextView tv = Ui.text(c, it.text, 16, it.checked ? R.color.accent_text : R.color.text_primary);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = Ui.weight(1);
        lp.setMarginStart(Ui.dp(c, 18));
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
