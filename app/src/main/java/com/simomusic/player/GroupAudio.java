package com.simomusic.player;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Group listening: the song that plays in the app is played at the same time on up to {@link #MAX} Bluetooth
 * outputs. The main player (the one the notification and lock screen control) drives the first selected output;
 * every other output gets its own silent-controlled "follower" player that mirrors the queue, play / pause, seeks
 * and speed of the main one, is pinned to its own headset, and is kept aligned to the main player by a small
 * drift corrector. Each follower has a delay trim (ms) because Bluetooth headsets differ in latency.
 *
 * Android decides which Bluetooth outputs an app may route to: only outputs listed by the system are offered here.
 * When the phone exposes just one Bluetooth output, the group simply has one member.
 */
@OptIn(markerClass = UnstableApi.class)
public final class GroupAudio {
    public static final int MAX = 5;
    public static final int OFFSET_MAX = 600, OFFSET_STEP = 20;
    static final String K_ON = "grp_on", K_SEL = "grp_sel", K_OFF = "grp_off_";

    private static GroupAudio inst;

    private final Context app;
    private final ExoPlayer master;
    private final AudioManager am;
    private final Handler h = new Handler(Looper.getMainLooper());

    /** One follower: its output key, the player that feeds it, and how many ms ahead of the main player it runs. */
    private static final class Follower {
        String key;
        ExoPlayer player;
        int offsetMs;
    }

    private final List<Follower> followers = new ArrayList<>();
    private boolean active, driftOn;
    private int members;
    private String masterKey;

    private final Player.Listener mirror = new Player.Listener() {
        @Override
        public void onTimelineChanged(Timeline t, int reason) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) safe(GroupAudio.this::reloadAll);
        }

        @Override
        public void onMediaItemTransition(MediaItem item, int reason) {
            safe(() -> alignAll());
        }

        @Override
        public void onPositionDiscontinuity(Player.PositionInfo oldPos, Player.PositionInfo newPos, int reason) {
            safe(() -> alignAll());
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            safe(() -> {
                for (Follower f : followers) f.player.setPlayWhenReady(playWhenReady);
                if (playWhenReady) alignAll();
            });
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters p) {
            safe(() -> {
                for (Follower f : followers) f.player.setPlaybackParameters(p);
            });
        }

        @Override
        public void onRepeatModeChanged(int mode) {
            safe(() -> {
                for (Follower f : followers) f.player.setRepeatMode(mode == Player.REPEAT_MODE_ONE ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
            });
        }
    };

    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            schedule();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            schedule();
        }
    };

    private final Runnable applyRun = () -> safe(this::apply);

    /** Keeps every follower within a few milliseconds of the main player while music plays. */
    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            safe(GroupAudio.this::correct);
            if (active) h.postDelayed(this, 1000);
            else driftOn = false;
        }
    };

    private GroupAudio(Context app, ExoPlayer master) {
        this.app = app;
        this.master = master;
        this.am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
    }

    // ------------------------------------------------------------------ lifecycle (service)

    public static void attach(Context c, ExoPlayer p) {
        detach();
        inst = new GroupAudio(c.getApplicationContext(), p);
        inst.start();
    }

    public static void detach() {
        if (inst != null) {
            inst.stop();
            inst = null;
        }
    }

    /** Selection or devices changed: rebuild the group. */
    public static void refresh() {
        if (inst != null) inst.schedule();
    }

    /** True while two or more outputs are playing together. */
    public static boolean active() {
        return inst != null && inst.active;
    }

    /** How many outputs are playing right now (0 when the group is off). */
    public static int members() {
        return inst == null ? 0 : inst.members;
    }

    private void start() {
        master.addListener(mirror);
        if (am != null) am.registerAudioDeviceCallback(devices, h);
        schedule();
    }

    private void stop() {
        h.removeCallbacksAndMessages(null);
        try {
            master.removeListener(mirror);
        } catch (RuntimeException ignored) {
        }
        try {
            if (am != null) am.unregisterAudioDeviceCallback(devices);
        } catch (RuntimeException ignored) {
        }
        releaseFollowers();
        active = false;
        driftOn = false;
        members = 0;
    }

    private void schedule() {
        h.removeCallbacks(applyRun);
        h.postDelayed(applyRun, 250);
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("group listening", e);
        }
    }

    // ------------------------------------------------------------------ building the group

    private void apply() {
        List<AudioDeviceInfo> want = new ArrayList<>();
        if (enabled(app)) {
            List<AudioDeviceInfo> have = candidates(app);
            for (String k : selection(app)) {
                for (AudioDeviceInfo d : have) {
                    if (key(d).equals(k) && want.size() < MAX) {
                        want.add(d);
                        break;
                    }
                }
            }
        }
        boolean wasActive = active;
        if (want.size() < 2) {
            // nothing to share: back to the normal single-headset routing
            releaseFollowers();
            active = false;
            members = want.size();
            masterKey = null;
            h.removeCallbacks(drift);
            driftOn = false;
            if (wasActive) BtAudio.refresh();
            return;
        }

        active = true;
        members = want.size();
        masterKey = key(want.get(0));
        master.setPreferredAudioDevice(want.get(0));

        // drop followers that are no longer wanted
        for (int i = followers.size() - 1; i >= 0; i--) {
            Follower f = followers.get(i);
            boolean keep = false;
            for (int j = 1; j < want.size(); j++) if (key(want.get(j)).equals(f.key)) keep = true;
            if (!keep) {
                release(f);
                followers.remove(i);
            }
        }
        // add the new ones, refresh the kept ones (the device id changes when a headset reconnects)
        for (int j = 1; j < want.size(); j++) {
            AudioDeviceInfo d = want.get(j);
            String k = key(d);
            Follower f = null;
            for (Follower x : followers) if (x.key.equals(k)) f = x;
            if (f == null) {
                f = make(d, k);
                if (f == null) continue;
                followers.add(f);
            } else {
                f.player.setPreferredAudioDevice(d);
            }
            f.offsetMs = offset(app, k);
        }
        members = 1 + followers.size();
        if (!driftOn) {
            driftOn = true;
            h.postDelayed(drift, 1000);
        }
    }

    private Follower make(AudioDeviceInfo d, String k) {
        try {
            ExoPlayer p = new ExoPlayer.Builder(app)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
                    .setHandleAudioBecomingNoisy(false)
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .build();
            p.setPreferredAudioDevice(d);
            p.setRepeatMode(master.getRepeatMode() == Player.REPEAT_MODE_ONE ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
            p.setPlaybackParameters(master.getPlaybackParameters());
            Follower f = new Follower();
            f.key = k;
            f.player = p;
            f.offsetMs = offset(app, k);
            load(f);
            return f;
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("group follower", e);
            return null;
        }
    }

    private static void release(Follower f) {
        try {
            f.player.stop();
            f.player.release();
        } catch (RuntimeException ignored) {
        }
    }

    private void releaseFollowers() {
        for (Follower f : followers) release(f);
        followers.clear();
    }

    // ------------------------------------------------------------------ mirroring

    private long target(Follower f) {
        return Math.max(0L, master.getCurrentPosition() + f.offsetMs);
    }

    /** Copies the queue and the current spot of the main player into one follower. */
    private void load(Follower f) {
        int n = master.getMediaItemCount();
        if (n == 0) {
            f.player.clearMediaItems();
            return;
        }
        List<MediaItem> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) items.add(master.getMediaItemAt(i));
        f.player.setMediaItems(items, master.getCurrentMediaItemIndex(), target(f));
        f.player.prepare();
        f.player.setPlayWhenReady(master.getPlayWhenReady());
    }

    private void reloadAll() {
        for (Follower f : followers) load(f);
    }

    /** Puts every follower on the main player's song and position (plus its own delay trim). */
    private void alignAll() {
        if (master.getMediaItemCount() == 0) return;
        int idx = master.getCurrentMediaItemIndex();
        for (Follower f : followers) {
            if (idx < f.player.getMediaItemCount()) f.player.seekTo(idx, target(f));
            else load(f);
        }
    }

    /** Small differences are smoothed by a barely audible speed change; big ones are corrected with a seek. */
    private void correct() {
        if (!master.isPlaying()) return;
        PlaybackParameters base = master.getPlaybackParameters();
        int idx = master.getCurrentMediaItemIndex();
        for (Follower f : followers) {
            ExoPlayer p = f.player;
            if (p.getPlaybackState() != Player.STATE_READY) continue;
            if (p.getCurrentMediaItemIndex() != idx) {
                alignAll();
                return;
            }
            long diff = p.getCurrentPosition() - target(f);
            if (Math.abs(diff) > 250) {
                p.seekTo(idx, target(f));
            } else if (Math.abs(diff) > 25) {
                float k = diff > 0 ? 0.985f : 1.015f;
                p.setPlaybackParameters(new PlaybackParameters(base.speed * k, base.pitch));
            } else if (p.getPlaybackParameters().speed != base.speed) {
                p.setPlaybackParameters(base);
            }
        }
    }

    // ------------------------------------------------------------------ choices (shared with the UI)

    public static boolean enabled(Context c) {
        return Store.flag(c, K_ON, false);
    }

    public static void setEnabled(Context c, boolean on) {
        Store.setFlag(c, K_ON, on);
        refresh();
    }

    /** Outputs the system lets the app route to (calls-only SCO is not music-capable). */
    public static List<AudioDeviceInfo> candidates(Context c) {
        List<AudioDeviceInfo> l = new ArrayList<>();
        for (AudioDeviceInfo d : BtAudio.outputs(c)) if (d.getType() != 7) l.add(d);
        return l;
    }

    /** Stable id of an output: its address when Android gives one, else its name (device ids change on reconnect). */
    public static String key(AudioDeviceInfo d) {
        String a = "";
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                a = d.getAddress();
            } catch (RuntimeException ignored) {
            }
        }
        if (a == null || a.isEmpty()) a = String.valueOf(d.getProductName());
        return a.replace(",", " ").trim();
    }

    public static String label(Context c, AudioDeviceInfo d) {
        CharSequence n = d.getProductName();
        return n == null || n.length() == 0 ? c.getString(R.string.bt_t_other) : n.toString();
    }

    /** Selected output keys in the order they were chosen; the first one is the reference the others follow. */
    public static List<String> selection(Context c) {
        List<String> out = new ArrayList<>();
        String s = Store.prefs(c).getString(K_SEL, "");
        if (s == null || s.isEmpty()) return out;
        for (String k : s.split(",")) if (!k.isEmpty() && !out.contains(k)) out.add(k);
        return out;
    }

    public static boolean isSelected(Context c, String key) {
        return selection(c).contains(key);
    }

    /** Returns false when the group is already full (5). */
    public static boolean setSelected(Context c, String key, boolean on) {
        List<String> sel = selection(c);
        if (on) {
            if (sel.contains(key)) return true;
            if (sel.size() >= MAX) return false;
            sel.add(key);
        } else {
            sel.remove(key);
        }
        StringBuilder sb = new StringBuilder();
        for (String k : sel) sb.append(k).append(',');
        Store.prefs(c).edit().putString(K_SEL, sb.toString()).apply();
        refresh();
        return true;
    }

    public static int offset(Context c, String key) {
        return Store.prefs(c).getInt(K_OFF + key, 0);
    }

    public static void setOffset(Context c, String key, int ms) {
        int v = Math.max(-OFFSET_MAX, Math.min(OFFSET_MAX, ms));
        Store.prefs(c).edit().putInt(K_OFF + key, v).apply();
        final GroupAudio g = inst;
        if (g != null) {
            for (Follower f : g.followers) if (f.key.equals(key)) f.offsetMs = v;
            g.h.post(() -> safe(g::alignAll));
        }
    }
}
