package com.nagham.player;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/** Your own 10-band equalizer for the Bluetooth headset: -8 to +8 dB per band. */
public final class EqSheet {
    private EqSheet() {
    }

    private static final String[] LABELS = {"31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"};

    private static String db(float v) {
        return (v > 0 ? "+" : "") + String.format(Locale.US, "%.1f dB", v);
    }

    public static void show(final Context c) {
        if (Dlg.dead(c)) return;
        final float[] g = BtAudio.customCurve(c);
        BtAudio.saveCustom(c, g);   // freeze the starting point so it does not follow the built-in profile any more
        final Dialog d = new Dialog(c, R.style.AppSheet);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_sheet);
        root.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 20));
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        View grab = new View(c);
        grab.setBackgroundResource(R.drawable.bg_grabber);
        LinearLayout.LayoutParams gp = Ui.lp(Ui.dp(c, 40), Ui.dp(c, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = Ui.dp(c, 4);
        gp.bottomMargin = Ui.dp(c, 8);
        head.addView(grab, gp);
        TextView title = Ui.text(c, c.getString(R.string.eq_title), 17, R.color.text_primary);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 10));
        head.addView(title);
        root.addView(head);
        Ui.dragDismiss(head, root, d);

        final int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.7f);
        ScrollView sv = new ScrollView(c) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), 0);
        sv.addView(body);
        root.addView(sv);

        final SeekBar[] bars = new SeekBar[g.length];
        final TextView[] vals = new TextView[g.length];
        final int acc = Ui.color(c, R.color.accent);
        for (int i = 0; i < g.length; i++) {
            final int band = i;
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            TextView l = Ui.text(c, LABELS[i], 13, R.color.text_secondary);
            row.addView(l, new LinearLayout.LayoutParams(Ui.dp(c, 58), -2));
            SeekBar sb = new SeekBar(c);
            sb.setMax(160);
            sb.setProgress(Math.round((g[i] + 8f) * 10f));
            sb.setProgressTintList(ColorStateList.valueOf(acc));
            sb.setThumbTintList(ColorStateList.valueOf(acc));
            sb.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.color(c, R.color.stroke)));
            bars[i] = sb;
            row.addView(sb, new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f));
            TextView v = Ui.text(c, db(g[i]), 13, R.color.text_primary);
            v.setGravity(Gravity.END);
            vals[i] = v;
            row.addView(v, new LinearLayout.LayoutParams(Ui.dp(c, 66), -2));
            sb.setOnTouchListener((x, e) -> {
                if (x.getParent() != null) x.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean user) {
                    if (!user) return;
                    g[band] = Math.round(p - 80) / 10f;
                    vals[band].setText(db(g[band]));
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                    Ui.tap(s);
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                    BtAudio.saveCustom(c, g);
                    BtAudio.refresh();
                }
            });
            body.addView(row);
        }

        Button reset = Ui.pill(c, R.string.eq_reset, 0, false, v -> {
            for (int i = 0; i < g.length; i++) {
                g[i] = 0f;
                bars[i].setProgress(80);
                vals[i].setText(db(0f));
            }
            BtAudio.saveCustom(c, g);
            BtAudio.refresh();
        });
        LinearLayout.LayoutParams rp = Ui.lp(-1, Ui.dp(c, 52));
        rp.setMargins(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), 0);
        reset.setLayoutParams(rp);
        reset.setGravity(Gravity.CENTER);
        root.addView(reset);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        Dlg.bind(c, d, null);
        d.show();
    }
}
