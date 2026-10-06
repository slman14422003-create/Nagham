package com.nagham.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Details of the connected Bluetooth headset plus the two Bluetooth sound options. */
public final class BtSheet {
    private BtSheet() {
    }

    public static void show(final Context c) {
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
        TextView t = Ui.text(c, c.getString(R.string.bt_title), 17, R.color.text_primary);
        t.setTypeface(Typeface.create("serif", Typeface.BOLD));
        t.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 10));
        head.addView(t);
        root.addView(head);
        Ui.dragDismiss(head, root, d);

        final int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.78f);
        ScrollView sv = new ScrollView(c) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        final LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
        sv.addView(body);
        root.addView(sv);

        TextView loading = Ui.text(c, c.getString(R.string.bt_loading), 15, R.color.text_secondary);
        loading.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        body.addView(loading);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        final boolean[] alive = {true};
        Dlg.bind(c, d, () -> alive[0] = false);
        d.show();

        BtInfo.load(c, info -> {
            if (!alive[0] || !d.isShowing()) return;
            body.removeAllViews();
            fill(c, d, body, info);
        });
    }

    private static void fill(final Context c, final Dialog d, LinearLayout body, BtInfo i) {
        if (!i.hasDevice) {
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 6));
            box.addView(badge(c, false));
            TextView a = Ui.text(c, c.getString(R.string.bt_none), 18, R.color.text_primary);
            a.setTypeface(Typeface.create("serif", Typeface.BOLD));
            a.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ap = Ui.lp(-1, -2);
            ap.topMargin = Ui.dp(c, 14);
            box.addView(a, ap);
            TextView b = Ui.text(c, c.getString(R.string.bt_none_sub), 14, R.color.text_secondary);
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bp = Ui.lp(-1, -2);
            bp.topMargin = Ui.dp(c, 6);
            box.addView(b, bp);
            body.addView(box);
            body.addView(settingsButton(c, d));
            body.addView(options(c));
            return;
        }

        // header: badge + name + kind
        LinearLayout hd = new LinearLayout(c);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 14));
        hd.addView(badge(c, true));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView nm = Ui.text(c, i.name.isEmpty() ? c.getString(R.string.bt_t_other) : i.name, 20, R.color.text_primary);
        nm.setTypeface(Typeface.create("serif", Typeface.BOLD));
        nm.setMaxLines(2);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(nm);
        TextView kind = Ui.text(c, kindLabel(c, i), 14, R.color.accent_text);
        kind.setPadding(0, Ui.dp(c, 3), 0, 0);
        col.addView(kind);
        LinearLayout.LayoutParams cp = Ui.weight(1);
        cp.setMarginStart(Ui.dp(c, 14));
        hd.addView(col, cp);
        body.addView(hd);

        if (i.needPerm) {
            body.addView(permButton(c, d));
        }

        body.addView(options(c));

        // facts
        LinearLayout facts = new LinearLayout(c);
        facts.setOrientation(LinearLayout.VERTICAL);
        facts.setBackgroundResource(R.drawable.bg_card);
        facts.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6));
        addFact(c, facts, R.string.bt_row_type, typeLabel(c, i.audioType));
        if (!i.address.isEmpty()) addFact(c, facts, R.string.bt_row_addr, i.address.toUpperCase(Locale.ROOT));
        if (!i.needPerm) addFact(c, facts, R.string.bt_row_pair, c.getString(i.bonded ? R.string.bt_paired : R.string.bt_not_paired));
        String prof = profiles(c, i);
        if (!prof.isEmpty()) addFact(c, facts, R.string.bt_row_profiles, prof);
        if (i.a2dp || i.audioType == 8) addFact(c, facts, R.string.bt_row_stream, c.getString(i.playing ? R.string.bt_playing : R.string.bt_idle));
        if (i.battery >= 0) addFact(c, facts, R.string.bt_row_battery, i.battery + "%");
        addFact(c, facts, R.string.bt_row_rates, rates(c, i.rates));
        addFact(c, facts, R.string.bt_row_channels, channels(c, i.channels));
        String fm = formats(i.encodings);
        if (!fm.isEmpty()) addFact(c, facts, R.string.bt_row_formats, fm);
        LinearLayout.LayoutParams fp = Ui.lp(-1, -2);
        fp.bottomMargin = Ui.dp(c, 10);
        body.addView(facts, fp);

        TextView note = Ui.text(c, c.getString(R.string.bt_codec), 13, R.color.text_hint);
        note.setLineSpacing(0, 1.1f);
        note.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 4));
        body.addView(note);
        body.addView(settingsButton(c, d));
    }

    private static final int[] PROF_T = {R.string.bt_profile_0, R.string.bt_profile_1, R.string.bt_profile_2, R.string.bt_profile_3, R.string.bt_profile_4};
    private static final int[] PROF_S = {R.string.bt_profile_0_sub, R.string.bt_profile_1_sub, R.string.bt_profile_2_sub, R.string.bt_profile_3_sub, R.string.bt_profile_4_sub};

    /** Sound tuning: profile, loudness boost, compressor, master switch, keep-on-headset. Rebuilds itself on every change. */
    private static View options(final Context c) {
        final LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            wrap.removeAllViews();
            TextView sec = Ui.text(c, c.getString(R.string.bt_tune), 14, R.color.accent_text);
            sec.setTypeface(Typeface.DEFAULT_BOLD);
            sec.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 8));
            wrap.addView(sec);

            // profile
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
            int cur = BtAudio.profile(c);
            for (int i = 0; i < PROF_T.length; i++) {
                final int p = i;
                card.addView(choice(c, PROF_T[i], PROF_S[i], i == cur, v -> {
                    Ui.tap(v);
                    BtAudio.setInt(c, BtAudio.K_PROFILE, p);
                    BtAudio.refresh();
                    fill[0].run();
                }));
            }
            LinearLayout.LayoutParams cp = Ui.lp(-1, -2);
            cp.bottomMargin = Ui.dp(c, 10);
            wrap.addView(card, cp);

            // loudness
            LinearLayout lc = new LinearLayout(c);
            lc.setOrientation(LinearLayout.VERTICAL);
            lc.setBackgroundResource(R.drawable.bg_card);
            lc.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 16));
            TextView lt = Ui.text(c, c.getString(R.string.bt_loud), 16, R.color.text_primary);
            lc.addView(lt);
            TextView ls = Ui.text(c, c.getString(R.string.bt_loud_sub), 13, R.color.text_secondary);
            ls.setPadding(0, Ui.dp(c, 3), 0, Ui.dp(c, 10));
            lc.addView(ls);
            LinearLayout seg = new LinearLayout(c);
            seg.setBackgroundResource(R.drawable.bg_segment);
            seg.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
            String[] names = {c.getString(R.string.bt_loud_off), "+3 dB", "+6 dB", "+9 dB"};
            int bs = BtAudio.boost(c);
            for (int i = 0; i < names.length; i++) {
                final int k = i;
                TextView b = Ui.text(c, names[i], 14, i == bs ? R.color.on_accent : R.color.text_secondary);
                b.setGravity(Gravity.CENTER);
                b.setMinHeight(Ui.dp(c, 42));
                b.setTypeface(i == bs ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                if (i == bs) b.setBackgroundResource(R.drawable.bg_segment_sel);
                b.setOnClickListener(v -> {
                    if (BtAudio.boost(c) == k) return;
                    Ui.tap(v);
                    BtAudio.setInt(c, BtAudio.K_BOOST, k);
                    BtAudio.refresh();
                    fill[0].run();
                });
                seg.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            lc.addView(seg);
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.bottomMargin = Ui.dp(c, 10);
            wrap.addView(lc, lp);

            // switches
            LinearLayout g = new LinearLayout(c);
            g.setOrientation(LinearLayout.VERTICAL);
            g.addView(Ui.toggleRow(c, c.getString(R.string.bt_opt), c.getString(R.string.bt_opt_sub), BtAudio.opt(c), (b2, on) -> {
                Store.setFlag(c, BtAudio.K_OPT, on);
                BtAudio.refresh();
            }));
            boolean ok = BtAudio.optSupported();
            View comp = Ui.toggleRow(c, c.getString(R.string.bt_comp),
                    ok ? c.getString(R.string.bt_comp_sub) : c.getString(R.string.bt_opt_old), ok && BtAudio.comp(c), (b2, on) -> {
                        Store.setFlag(c, BtAudio.K_COMP, on);
                        BtAudio.refresh();
                    });
            if (!ok) {
                comp.setEnabled(false);
                comp.setAlpha(0.5f);
            }
            g.addView(comp);
            g.addView(Ui.toggleRow(c, c.getString(R.string.bt_pin), c.getString(R.string.bt_pin_sub), BtAudio.pin(c), (b2, on) -> {
                Store.setFlag(c, BtAudio.K_PIN, on);
                BtAudio.refresh();
            }));
            Ui.group(c, g);
            LinearLayout.LayoutParams gp = Ui.lp(-1, -2);
            gp.bottomMargin = Ui.dp(c, 8);
            wrap.addView(g, gp);
        };
        fill[0].run();
        return wrap;
    }

    private static View choice(Context c, int titleRes, int subRes, boolean on, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(c, 56));
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        r.setBackgroundResource(R.drawable.bg_ripple_rect);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(c, c.getString(titleRes), 16, on ? R.color.accent_text : R.color.text_primary);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        col.addView(t);
        TextView s = Ui.text(c, c.getString(subRes), 13, R.color.text_secondary);
        s.setPadding(0, Ui.dp(c, 2), 0, 0);
        col.addView(s);
        r.addView(col, Ui.weight(1));
        if (on) {
            ImageView ck = new ImageView(c);
            ck.setImageResource(R.drawable.ic_check);
            Ui.tint(ck, R.color.accent_text);
            LinearLayout.LayoutParams kp = Ui.lp(Ui.dp(c, 22), Ui.dp(c, 22));
            kp.setMarginStart(Ui.dp(c, 10));
            r.addView(ck, kp);
        }
        r.setOnClickListener(l);
        return r;
    }

    private static View settingsButton(final Context c, final Dialog d) {
        Button b = Ui.pill(c, R.string.bt_settings, 0, false, v -> {
            d.dismiss();
            try {
                c.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        });
        LinearLayout.LayoutParams p = Ui.lp(-1, Ui.dp(c, 52));
        p.setMargins(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), 0);
        b.setLayoutParams(p);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private static View permButton(final Context c, final Dialog d) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView s = Ui.text(c, c.getString(R.string.bt_perm_sub), 14, R.color.text_secondary);
        s.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 8));
        box.addView(s);
        Button b = Ui.pill(c, R.string.bt_perm, 0, true, v -> {
            if (c instanceof Activity) Perms.askBt((Activity) c);
            d.dismiss();
        });
        LinearLayout.LayoutParams p = Ui.lp(-1, Ui.dp(c, 52));
        p.setMargins(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 12));
        b.setLayoutParams(p);
        b.setGravity(Gravity.CENTER);
        box.addView(b);
        return box;
    }

    private static View badge(Context c, boolean on) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(R.drawable.ic_bluetooth);
        Ui.tint(iv, on ? R.color.on_accent : R.color.text_secondary);
        int p = Ui.dp(c, 16);
        iv.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? Ui.color(c, R.color.accent) : Ui.color(c, R.color.surface_high));
        iv.setBackground(g);
        iv.setLayoutParams(Ui.lp(Ui.dp(c, 64), Ui.dp(c, 64)));
        return iv;
    }

    private static void addFact(Context c, LinearLayout g, int labelRes, String value) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
        TextView l = Ui.text(c, c.getString(labelRes), 14, R.color.text_secondary);
        r.addView(l, Ui.lp(-2, -2));
        TextView v = Ui.text(c, value, 15, R.color.text_primary);
        v.setGravity(Gravity.END);
        LinearLayout.LayoutParams vp = Ui.weight(1);
        vp.setMarginStart(Ui.dp(c, 16));
        r.addView(v, vp);
        if (g.getChildCount() > 0) {
            View line = new View(c);
            line.setBackgroundColor(Ui.color(c, R.color.stroke_soft));
            g.addView(line, Ui.lp(-1, 1));
        }
        g.addView(r);
    }

    // ------------------------------------------------------------------ labels

    private static String typeLabel(Context c, int t) {
        switch (t) {
            case 8:
                return c.getString(R.string.bt_t_a2dp);
            case 26:
            case 27:
            case 30:
                return c.getString(R.string.bt_t_le);
            case 7:
                return c.getString(R.string.bt_t_sco);
            case 23:
                return c.getString(R.string.bt_t_hearing);
            default:
                return c.getString(R.string.bt_t_other);
        }
    }

    private static String kindLabel(Context c, BtInfo i) {
        int k = i.btClass;
        if (k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES) return c.getString(R.string.bt_c_headphones);
        if (k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET
                || k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE) return c.getString(R.string.bt_c_headset);
        if (k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER
                || k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO
                || k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO) return c.getString(R.string.bt_c_speaker);
        if (k == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) return c.getString(R.string.bt_c_car);
        if (i.audioType == 23) return c.getString(R.string.bt_t_hearing);
        return c.getString(R.string.bt_c_other);
    }

    private static String profiles(Context c, BtInfo i) {
        StringBuilder sb = new StringBuilder();
        if (i.a2dp || i.audioType == 8) sb.append("A2DP");
        if (i.audioType == 26 || i.audioType == 27 || i.audioType == 30) sb.append("LE Audio");
        if (i.hfp || i.audioType == 7) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(c.getString(R.string.bt_hfp));
        }
        return sb.toString();
    }

    private static String rates(Context c, int[] r) {
        if (r == null || r.length == 0) return c.getString(R.string.bt_any);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.length; i++) {
            if (i > 0) sb.append(" · ");
            float k = r[i] / 1000f;
            sb.append(k == (int) k ? String.valueOf((int) k) : String.format(Locale.US, "%.1f", k));
        }
        return sb.append(" kHz").toString();
    }

    private static String channels(Context c, int[] ch) {
        if (ch == null || ch.length == 0) return c.getString(R.string.bt_stereo);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ch.length; i++) {
            if (i > 0) sb.append(" · ");
            sb.append(ch[i] == 1 ? c.getString(R.string.bt_mono) : ch[i] == 2 ? c.getString(R.string.bt_stereo) : String.valueOf(ch[i]));
        }
        return sb.toString();
    }

    private static String formats(int[] enc) {
        if (enc == null || enc.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int e : enc) {
            String s = null;
            if (e == AudioFormat.ENCODING_PCM_16BIT) s = "PCM 16-bit";
            else if (e == AudioFormat.ENCODING_PCM_8BIT) s = "PCM 8-bit";
            else if (e == AudioFormat.ENCODING_PCM_FLOAT) s = "PCM float";
            else if (e == 21) s = "PCM 24-bit";
            else if (e == 22) s = "PCM 32-bit";
            if (s == null) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(s);
        }
        return sb.toString();
    }
}
