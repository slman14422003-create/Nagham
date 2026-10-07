package com.simomusic.player;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Group listening: pick up to 5 Bluetooth headphones to play the same song at the same time, and trim each one's delay. */
public final class GroupSheet {
    private GroupSheet() {
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
        TextView t = Ui.text(c, c.getString(R.string.grp_title), 17, R.color.text_primary);
        t.setTypeface(Ui.titleFace(true));
        t.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 10));
        head.addView(t);
        root.addView(head);
        Ui.dragDismiss(head, root, d);

        final int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.80f);
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

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable[] fill = new Runnable[1];
        final String[] shown = {""};
        fill[0] = () -> {
            if (!d.isShowing()) return;
            List<AudioDeviceInfo> have = GroupAudio.candidates(c);
            shown[0] = signature(have);
            build(c, d, body, have, fill[0], h);
        };
        // devices come and go while the sheet is open: rebuild only when the list really changed
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (!d.isShowing()) return;
            if (!signature(GroupAudio.candidates(c)).equals(shown[0])) fill[0].run();
            h.postDelayed(tick[0], 2000);
        };
        Dlg.bind(c, d, () -> h.removeCallbacksAndMessages(null));
        d.show();
        fill[0].run();
        h.postDelayed(tick[0], 2000);
    }

    private static String signature(List<AudioDeviceInfo> l) {
        StringBuilder sb = new StringBuilder();
        for (AudioDeviceInfo d : l) sb.append(GroupAudio.key(d)).append('|');
        return sb.toString();
    }

    private static void build(final Context c, final Dialog d, LinearLayout body, List<AudioDeviceInfo> have,
                              final Runnable refill, final Handler h) {
        body.removeAllViews();

        // master switch
        LinearLayout sw = new LinearLayout(c);
        sw.setOrientation(LinearLayout.VERTICAL);
        sw.addView(Ui.toggleRow(c, c.getString(R.string.grp_switch), c.getString(R.string.grp_switch_sub), GroupAudio.enabled(c), (b, on) -> {
            GroupAudio.setEnabled(c, on);
            h.postDelayed(refill, 700);      // the group needs a moment to start, then the status line catches up
        }));
        Ui.group(c, sw);
        body.addView(sw, Ui.lp(-1, -2));

        // status
        final TextView status = Ui.text(c, statusText(c, have), 14, R.color.accent_text);
        status.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 4));
        body.addView(status);

        // headphones
        List<String> sel = GroupAudio.selection(c);
        int chosen = 0;
        for (AudioDeviceInfo x : have) if (sel.contains(GroupAudio.key(x))) chosen++;
        TextView sec = Ui.text(c, c.getString(R.string.grp_pick, chosen, GroupAudio.MAX), 14, R.color.accent_text);
        sec.setTypeface(Typeface.DEFAULT_BOLD);
        sec.setPadding(Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 8));
        body.addView(sec);

        if (have.isEmpty()) {
            TextView none = Ui.text(c, c.getString(R.string.grp_none), 14, R.color.text_secondary);
            none.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 8));
            body.addView(none);
        } else {
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            for (final AudioDeviceInfo dev : have) {
                final String key = GroupAudio.key(dev);
                int pos = sel.indexOf(key);
                String trailing = pos < 0 ? c.getString(R.string.grp_add)
                        : pos == 0 ? c.getString(R.string.grp_main) : String.valueOf(pos + 1);
                list.addView(Ui.settingRow(c, R.drawable.ic_bluetooth, GroupAudio.label(c, dev), null, trailing, pos < 0, v -> {
                    boolean on = !GroupAudio.isSelected(c, key);
                    if (!GroupAudio.setSelected(c, key, on)) {
                        Ui.toast(c, R.string.grp_full);
                        return;
                    }
                    refill.run();
                    h.postDelayed(refill, 700);
                }));
            }
            Ui.group(c, list);
            body.addView(list, Ui.lp(-1, -2));
        }

        // per-headphone delay trim for everyone except the main output
        final List<AudioDeviceInfo> followers = new ArrayList<>();
        for (int i = 1; i < sel.size(); i++) {
            for (AudioDeviceInfo x : have) if (GroupAudio.key(x).equals(sel.get(i))) followers.add(x);
        }
        if (!followers.isEmpty()) {
            TextView ts = Ui.text(c, c.getString(R.string.grp_trim), 14, R.color.accent_text);
            ts.setTypeface(Typeface.DEFAULT_BOLD);
            ts.setPadding(Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 2));
            body.addView(ts);
            TextView tn = Ui.text(c, c.getString(R.string.grp_trim_sub), 13, R.color.text_secondary);
            tn.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 8));
            body.addView(tn);
            for (AudioDeviceInfo x : followers) body.addView(trimRow(c, x));
        }

        TextView note = Ui.text(c, c.getString(R.string.grp_note), 13, R.color.text_hint);
        note.setLineSpacing(0, 1.1f);
        note.setPadding(Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 4));
        body.addView(note);

        Button bt = Ui.pill(c, R.string.bt_settings, 0, false, v -> {
            d.dismiss();
            try {
                c.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        });
        LinearLayout.LayoutParams bp = Ui.lp(-1, Ui.dp(c, 52));
        bp.setMargins(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), 0);
        bt.setLayoutParams(bp);
        bt.setGravity(Gravity.CENTER);
        body.addView(bt);
    }

    private static String statusText(Context c, List<AudioDeviceInfo> have) {
        if (!GroupAudio.enabled(c)) return c.getString(R.string.grp_status_off);
        List<String> sel = GroupAudio.selection(c);
        int avail = 0;
        for (AudioDeviceInfo x : have) if (sel.contains(GroupAudio.key(x))) avail++;
        if (GroupAudio.active()) return c.getString(R.string.grp_status_on, GroupAudio.members());
        if (sel.size() < 2) return c.getString(R.string.grp_status_need);
        return c.getString(R.string.grp_status_wait, avail, sel.size());
    }

    /** One headphone: name, current trim, and − / + in 20 ms steps (+ = this headphone plays slightly ahead). */
    private static View trimRow(final Context c, final AudioDeviceInfo dev) {
        final String key = GroupAudio.key(dev);
        LinearLayout card = new LinearLayout(c);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(c, GroupAudio.label(c, dev), 15, R.color.text_primary));
        final TextView val = Ui.text(c, fmt(GroupAudio.offset(c, key)), 13, R.color.text_secondary);
        val.setPadding(0, Ui.dp(c, 2), 0, 0);
        col.addView(val);
        card.addView(col, Ui.weight(1));

        TextView minus = Ui.chip(c, "−", false);
        TextView plus = Ui.chip(c, "+", false);
        minus.setContentDescription(c.getString(R.string.grp_less));
        plus.setContentDescription(c.getString(R.string.grp_more));
        minus.setMinWidth(Ui.dp(c, 48));
        plus.setMinWidth(Ui.dp(c, 48));
        minus.setGravity(Gravity.CENTER);
        plus.setGravity(Gravity.CENTER);
        minus.setOnClickListener(v -> step(c, key, -GroupAudio.OFFSET_STEP, val));
        plus.setOnClickListener(v -> step(c, key, GroupAudio.OFFSET_STEP, val));
        card.addView(minus);
        card.addView(plus);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(c, 8);
        card.setLayoutParams(lp);
        return card;
    }

    private static void step(Context c, String key, int delta, TextView val) {
        Ui.tap(val);
        GroupAudio.setOffset(c, key, GroupAudio.offset(c, key) + delta);
        val.setText(fmt(GroupAudio.offset(c, key)));
    }

    private static String fmt(int ms) {
        return String.format(Locale.US, "%+d ms", ms);
    }

    /** The row shown at the top of the Bluetooth sheet. */
    public static View entry(final Context c, final Dialog parent) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        String sub = GroupAudio.active() ? c.getString(R.string.grp_status_on, GroupAudio.members()) : c.getString(R.string.grp_entry_sub);
        g.addView(Ui.settingRow(c, R.drawable.ic_bluetooth, c.getString(R.string.grp_title), sub, null, false, v -> {
            parent.dismiss();
            show(c);
        }));
        Ui.group(c, g);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(c, 12);
        g.setLayoutParams(lp);
        return g;
    }
}
