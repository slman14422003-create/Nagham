package com.nagham.player;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/** Settings: audio formats, playback, lock screen, and the permission center (status + one tap to grant). */
public class SettingsActivity extends AppCompatActivity {
    private LinearLayout perms;
    private boolean dirty;
    private final TextView[] modeBtns = new TextView[3];

    /** Theme mode (auto / light / dark) and the accent color swatches. */
    private View appearanceCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));

        TextView t1 = Ui.text(this, getString(R.string.theme_mode), 15, R.color.text_secondary);
        t1.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(t1);
        LinearLayout seg = new LinearLayout(this);
        seg.setBackgroundResource(R.drawable.bg_segment);
        seg.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        int[] labels = {R.string.theme_auto, R.string.theme_light, R.string.theme_dark};
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView b = Ui.text(this, getString(labels[i]), 15, R.color.text_secondary);
            b.setGravity(android.view.Gravity.CENTER);
            b.setMinHeight(Ui.dp(this, 44));
            b.setOnClickListener(v -> {
                if (Accent.mode(this) == m) return;
                Ui.tap(v);
                Accent.setMode(this, m);   // AppCompat re-creates the screens when day / night really changes
                styleModes();
            });
            modeBtns[i] = b;
            seg.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = Ui.dp(this, 10);
        card.addView(seg, sp);
        styleModes();

        TextView t2 = Ui.text(this, getString(R.string.accent_color), 15, R.color.text_secondary);
        t2.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = Ui.dp(this, 18);
        card.addView(t2, tp);

        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int sel = Accent.index(this);
        for (int row = 0; row < 2; row++) {
            LinearLayout r = new LinearLayout(this);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = Ui.dp(this, 12);
            for (int k = 0; k < 4; k++) {
                final int i = row * 4 + k;
                FrameLayout cell = new FrameLayout(this);
                View dot = new View(this);
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                g.setColor(Accent.SWATCH[i][night ? 1 : 0]);
                if (i == sel) g.setStroke(Ui.dp(this, 3), Ui.color(this, R.color.text_primary));
                else g.setStroke(Ui.dp(this, 1), Ui.color(this, R.color.stroke));
                dot.setBackground(g);
                int d = Ui.dp(this, 46);
                cell.addView(dot, new FrameLayout.LayoutParams(d, d, android.view.Gravity.CENTER));
                if (i == sel) {
                    ImageView ck = new ImageView(this);
                    ck.setImageResource(R.drawable.ic_check);
                    ck.setColorFilter(0xFFFFFFFF);
                    int p = Ui.dp(this, 12);
                    ck.setPadding(p, p, p, p);
                    cell.addView(ck, new FrameLayout.LayoutParams(d, d, android.view.Gravity.CENTER));
                }
                cell.setOnClickListener(v -> {
                    if (Accent.index(this) == i) return;
                    Ui.tap(v);
                    Accent.set(this, i);
                    App.recreateAll();
                });
                r.addView(cell, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
            }
            card.addView(r, rp);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        card.setLayoutParams(cp);
        return card;
    }

    private void styleModes() {
        int cur = Accent.mode(this);
        for (int i = 0; i < 3; i++) {
            TextView b = modeBtns[i];
            boolean on = i == cur;
            b.setTextColor(Ui.color(this, on ? R.color.on_accent : R.color.text_secondary));
            b.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            if (on) b.setBackgroundResource(R.drawable.bg_segment_sel);
            else b.setBackground(null);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Ui.topBar(this, getString(R.string.settings), R.drawable.ic_back));
        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Ui.dp(this, 32));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        getWindow().setBackgroundDrawable(Ui.glow(this));

        // ---- appearance
        col.addView(Ui.section(this, R.string.set_appearance));
        col.addView(appearanceCard());

        // ---- general
        col.addView(Ui.section(this, R.string.set_general));
        LinearLayout gen = group(col);
        gen.addView(Ui.settingRow(this, R.drawable.ic_language, getString(R.string.language), langLabel(), null, false, v -> pickLang()));
        Ui.group(this, gen);

        // ---- formats
        col.addView(Ui.section(this, R.string.set_formats));
        LinearLayout fm = group(col);
        String[] names = getResources().getStringArray(R.array.fmt_names);
        String[] subs = getResources().getStringArray(R.array.fmt_subs);
        final boolean[] fmtOn = new boolean[Store.FORMATS.length];
        for (int i = 0; i < fmtOn.length; i++) fmtOn[i] = Store.formatOn(this, Store.FORMATS[i]);
        fm.addView(Ui.chipGrid(this, names, subs, fmtOn, (i, want, count) -> {
            if (!want && count <= 1) {
                Ui.toast(this, R.string.fmt_min_one);
                return false;
            }
            Store.setFormatOn(this, Store.FORMATS[i], want);
            dirty = true;
            return true;
        }));
        col.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.dp(this, 10)));
        LinearLayout lib = group(col);
        lib.addView(Ui.settingRow(this, R.drawable.ic_timer, getString(R.string.set_min_dur), minLabel(), null, false, v -> pickMin()));
        lib.addView(Ui.settingRow(this, R.drawable.ic_refresh, getString(R.string.rescan), getString(R.string.rescan_sub), null, false, v -> {
            Library.scan(this, () -> Ui.toast(this,
                    getResources().getQuantityString(R.plurals.songs_n, Library.tracks.size(), Library.tracks.size())));
            dirty = false;
        }));
        if (CrashGuard.has(this)) {
            lib.addView(Ui.settingRow(this, R.drawable.ic_info, getString(R.string.crash_row), getString(R.string.crash_row_sub), null, false,
                    v -> CrashGuard.share(this)));
        }
        Ui.group(this, lib);

        // ---- playback
        col.addView(Ui.section(this, R.string.set_playback));
        LinearLayout pb = group(col);
        pb.addView(Ui.toggleRow(this, getString(R.string.skip_silence), getString(R.string.skip_silence_sub),
                Store.flag(this, "skip_silence", false), (v, on) -> Store.setFlag(this, "skip_silence", on)));
        pb.addView(Ui.toggleRow(this, getString(R.string.resume_title), getString(R.string.resume_sub),
                Store.flag(this, "resume", true), (v, on) -> Store.setFlag(this, "resume", on)));
        Ui.group(this, pb);

        // ---- lock screen
        col.addView(Ui.section(this, R.string.set_lock));
        LinearLayout lk = group(col);
        lk.addView(Ui.toggleRow(this, getString(R.string.lock_auto), getString(R.string.lock_auto_sub),
                Store.flag(this, "lock_auto", true), (v, on) -> Store.setFlag(this, "lock_auto", on)));
        lk.addView(Ui.toggleRow(this, getString(R.string.lock_wake), getString(R.string.lock_wake_sub),
                Store.flag(this, "lock_wake", false), (v, on) -> Store.setFlag(this, "lock_wake", on)));
        lk.addView(Ui.toggleRow(this, getString(R.string.lock_blur), getString(R.string.lock_blur_sub),
                Store.flag(this, "lock_blur", true), (v, on) -> Store.setFlag(this, "lock_blur", on)));
        lk.addView(Ui.settingRow(this, R.drawable.ic_timer, getString(R.string.lock_test), getString(R.string.lock_test_sub),
                null, false, v -> {
                    if (LockLauncher.ready(this)) {
                        LockLauncher.testIn(this, 6);
                        Ui.toast(this, R.string.lock_test_toast);
                    } else {
                        Ui.toast(this, R.string.lock_test_missing);
                    }
                }));
        lk.addView(Ui.settingRow(this, R.drawable.ic_lock, getString(R.string.lock_preview), getString(R.string.lock_preview_sub),
                null, false, v -> Menus.openLock(this)));
        Ui.group(this, lk);

        // ---- permissions
        col.addView(Ui.section(this, R.string.set_perms));
        perms = group(col);
    }

    private LinearLayout group(LinearLayout parent) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        parent.addView(g, new LinearLayout.LayoutParams(-1, -2));
        return g;
    }

    private String langLabel() {
        String v = Lang.saved(this);
        return "ar".equals(v) ? "العربية" : "en".equals(v) ? "English" : getString(R.string.lang_system);
    }

    private void pickLang() {
        final String[] codes = {"", "ar", "en"};
        final String[] names = {getString(R.string.lang_system), "العربية", "English"};
        Sheet.show(this, getString(R.string.language), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            for (int i = 0; i < codes.length; i++) {
                final String code = codes[i];
                l.add(Sheet.item(R.drawable.ic_language, names[i], Lang.saved(this).equals(code), false, () -> Lang.set(this, code)));
            }
            return l;
        });
    }

    private String minLabel() {
        int s = Store.minDur(this);
        return s == 0 ? getString(R.string.min_none) : getString(R.string.min_sec, s);
    }

    private void pickMin() {
        Sheet.show(this, getString(R.string.set_min_dur), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            for (final int s : new int[]{0, 15, 30, 60, 120}) {
                l.add(Sheet.item(R.drawable.ic_timer, s == 0 ? getString(R.string.min_none) : getString(R.string.min_sec, s),
                        Store.minDur(this) == s, false, () -> {
                            Store.setMinDur(this, s);
                            dirty = true;
                            recreate();
                        }));
            }
            return l;
        });
    }

    private void fillPerms() {
        perms.removeAllViews();
        addPerm(R.drawable.ic_folder, R.string.perm_audio, R.string.perm_audio_sub, Perms.hasAudio(this), v -> Perms.askAudio(this));
        addPerm(R.drawable.ic_star, R.string.perm_notif, R.string.perm_notif_sub, Perms.hasNotif(this), v -> Perms.askNotif(this));
        if (Build.VERSION.SDK_INT >= 34) {
            addPerm(R.drawable.ic_lock, R.string.perm_fsi, R.string.perm_fsi_sub, Perms.hasFsi(this), v -> Perms.askFsi(this));
        }
        addPerm(R.drawable.ic_list, R.string.perm_overlay, R.string.perm_overlay_sub, Perms.hasOverlay(this), v -> Perms.askOverlay(this));
        addPerm(R.drawable.ic_star, R.string.perm_chan, R.string.perm_chan_sub, Perms.hasLockChannel(this), v -> Perms.askLockChannel(this));
        addPerm(R.drawable.ic_shield, R.string.perm_battery, R.string.perm_battery_sub, Perms.hasBattery(this), v -> Perms.askBattery(this));
        if (Build.VERSION.SDK_INT >= 31) {
            addPerm(R.drawable.ic_music, R.string.perm_bt, R.string.perm_bt_sub, Perms.hasBt(this), v -> Perms.askBt(this));
        }
        // vendor switches that Android cannot report: shown as "Open" so the user can check them once
        if (Oem.xiaomi()) {
            addOem(R.drawable.ic_lock, R.string.perm_xiaomi, R.string.perm_xiaomi_sub, v -> Oem.xiaomiPermissions(this));
            addOem(R.drawable.ic_refresh, R.string.perm_autostart, R.string.perm_autostart_sub, v -> Oem.xiaomiAutostart(this));
        }
        if (Oem.xiaomi() || Oem.samsung()) {
            addOem(R.drawable.ic_shield, R.string.perm_battery_oem, R.string.perm_battery_oem_sub, v -> Oem.battery(this));
        }
        Ui.group(this, perms);
    }

    private void addOem(int icon, int title, int sub, View.OnClickListener click) {
        perms.addView(Ui.settingRow(this, icon, getString(title), getString(sub), getString(R.string.perm_open), false, click));
    }

    private void addPerm(int icon, int title, int sub, boolean ok, View.OnClickListener click) {
        perms.addView(Ui.settingRow(this, icon, getString(title), getString(sub),
                getString(ok ? R.string.perm_on : R.string.perm_enable), ok, ok ? null : click));
    }

    @Override
    protected void onResume() {
        super.onResume();
        fillPerms();
    }

    @Override
    protected void onPause() {
        if (dirty) {
            dirty = false;
            Library.scan(this, null);
        }
        super.onPause();
    }
}
