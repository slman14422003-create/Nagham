package com.nagham.player;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/** Settings: audio formats, playback, lock screen, and the permission center (status + one tap to grant). */
public class SettingsActivity extends AppCompatActivity {
    private LinearLayout perms;
    private boolean dirty;

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

        // ---- formats
        col.addView(Ui.section(this, R.string.set_formats));
        LinearLayout fm = group(col);
        String[] names = getResources().getStringArray(R.array.fmt_names);
        String[] subs = getResources().getStringArray(R.array.fmt_subs);
        for (int i = 0; i < Store.FORMATS.length; i++) {
            final String key = Store.FORMATS[i];
            fm.addView(Ui.toggleRow(this, names[i], subs[i], Store.formatOn(this, key), (v, on) -> {
                Store.setFormatOn(this, key, on);
                dirty = true;
            }));
        }
        Ui.group(this, fm);
        LinearLayout lib = group(col);
        lib.addView(Ui.settingRow(this, R.drawable.ic_timer, getString(R.string.set_min_dur), minLabel(), null, false, v -> pickMin()));
        lib.addView(Ui.settingRow(this, R.drawable.ic_refresh, getString(R.string.rescan), getString(R.string.rescan_sub), null, false, v -> {
            Library.scan(this, () -> Toast.makeText(this, getString(R.string.songs_count, Library.tracks.size()), Toast.LENGTH_SHORT).show());
            dirty = false;
        }));
        Ui.group(this, lib);

        // ---- playback
        col.addView(Ui.section(this, R.string.set_playback));
        LinearLayout pb = group(col);
        pb.addView(Ui.toggleRow(this, getString(R.string.skip_silence), getString(R.string.skip_silence_sub),
                Store.flag(this, "skip_silence", false), (v, on) -> Store.setFlag(this, "skip_silence", on)));
        Ui.group(this, pb);

        // ---- lock screen
        col.addView(Ui.section(this, R.string.set_lock));
        LinearLayout lk = group(col);
        lk.addView(Ui.toggleRow(this, getString(R.string.lock_auto), getString(R.string.lock_auto_sub),
                Store.flag(this, "lock_auto", true), (v, on) -> Store.setFlag(this, "lock_auto", on)));
        lk.addView(Ui.toggleRow(this, getString(R.string.lock_blur), getString(R.string.lock_blur_sub),
                Store.flag(this, "lock_blur", true), (v, on) -> Store.setFlag(this, "lock_blur", on)));
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
        addPerm(R.drawable.ic_shield, R.string.perm_battery, R.string.perm_battery_sub, Perms.hasBattery(this), v -> Perms.askBattery(this));
        if (Build.VERSION.SDK_INT >= 31) {
            addPerm(R.drawable.ic_music, R.string.perm_bt, R.string.perm_bt_sub, Perms.hasBt(this), v -> Perms.askBt(this));
        }
        Ui.group(this, perms);
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
