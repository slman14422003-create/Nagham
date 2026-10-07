package com.simomusic.player;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Bluetooth output manager (lives with the player service): keeps playback on the headset, plays on connect, and
 * feeds the settings to the {@link BtDsp} sound engine. The codec itself (SBC / AAC / LDAC / aptX) is negotiated by
 * Android and the headset; apps cannot force it, so the engine prepares the best possible signal for it.
 */
@OptIn(markerClass = UnstableApi.class)
public final class BtAudio {
    public static final String K_PIN = "bt_pin", K_OPT = "bt_opt", K_PROFILE = "bt_profile", K_BOOST = "bt_boost",
            K_COMP = "bt_comp", K_WIDE = "bt_wide", K_AUTO = "bt_auto", K_EQ = "bt_eq", K_VBASS = "bt_vbass", K_ALL = "bt_all";
    /** Profiles 0-4 are built in, 5 is the user's own 10-band curve. */
    public static final int PROFILES = 6, CUSTOM = 5;

    /** Gain per band in dB (31 Hz ... 16 kHz). 0 = tuned for budget TWS: more sub-bass, less mud, softer highs. */
    static final float[][] CURVES = {
            {3.0f, 3.5f, 2.0f, -1.5f, -0.5f, 0f, 1.0f, -1.5f, -2.5f, -1.0f},   // Auto: budget TWS
            {1.0f, 1.0f, 0.5f, -0.5f, 0f, 0f, 0.5f, 0f, -1.0f, 0f},            // Balanced
            {5.0f, 5.0f, 3.0f, 1.0f, 0f, 0f, 0f, -0.5f, -1.0f, 0f},            // Bass
            {-1.0f, -1.0f, -0.5f, 0f, 1.0f, 2.0f, 2.5f, 1.0f, 0f, 0f},         // Vocal
            {0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f}                           // Flat
    };
    /** Loudness target per level: Off, Low, Medium, High (LUFS). */
    static final float[] TARGET_LUFS = {Float.NaN, -17f, -15f, -13f};

    private static BtAudio inst;

    private final Context app;
    private final ExoPlayer player;
    private final AudioManager am;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int pinnedId = -1;
    private long startedAt;

    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            schedule();
            // the callback also reports devices that were already connected when it was registered: skip those
            if (SystemClock.uptimeMillis() - startedAt < 2500 || !autoPlay(app)) return;
            for (AudioDeviceInfo d : added) {
                if (isBt(d.getType()) && d.getType() != 7) {
                    h.postDelayed(() -> {
                        try {
                            if (player.getMediaItemCount() > 0 && !player.getPlayWhenReady()) player.play();
                        } catch (Exception ignored) {
                        }
                    }, 700);
                    break;
                }
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            schedule();
        }
    };

    private final Runnable applyRun = this::applyNow;

    private BtAudio(Context app, ExoPlayer player) {
        this.app = app;
        this.player = player;
        this.am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
    }

    // ------------------------------------------------------------------ lifecycle (service)

    public static void attach(Context c, ExoPlayer p) {
        detach();
        inst = new BtAudio(c.getApplicationContext(), p);
        inst.start();
    }

    public static void detach() {
        if (inst != null) {
            inst.stop();
            inst = null;
        }
    }

    /** Settings changed: re-evaluate routing and the sound engine. */
    public static void refresh() {
        if (inst != null) inst.schedule();
    }

    private void start() {
        startedAt = SystemClock.uptimeMillis();
        if (am != null) am.registerAudioDeviceCallback(devices, h);
        schedule();
    }

    private void stop() {
        h.removeCallbacksAndMessages(null);
        try {
            if (am != null) am.unregisterAudioDeviceCallback(devices);
        } catch (Exception ignored) {
        }
        try {
            player.setPreferredAudioDevice(null);
        } catch (Exception ignored) {
        }
        BtDsp.Params off = new BtDsp.Params();
        BtDspProcessor.INSTANCE.setParams(off);
    }

    /** Headsets often report several events in a row while connecting; handle them once. */
    private void schedule() {
        h.removeCallbacks(applyRun);
        h.postDelayed(applyRun, 250);
    }

    private void applyNow() {
        AudioDeviceInfo target = primary(app);
        try {
            if (GroupAudio.active()) {
                pinnedId = -2;          // group listening owns the routing; re-pin as normal once it ends
            } else {
                AudioDeviceInfo want = (target != null && pin(app)) ? target : null;
                int wantId = want == null ? -1 : want.getId();
                if (wantId != pinnedId) {
                    player.setPreferredAudioDevice(want);
                    pinnedId = wantId;
                }
            }
        } catch (Exception ignored) {
        }
        BtDspProcessor.INSTANCE.setParams(params(app, target != null));
    }

    /** The sound-engine settings for the current choices; enabled on Bluetooth (or everywhere if chosen). */
    static BtDsp.Params params(Context c, boolean bluetooth) {
        BtDsp.Params p = new BtDsp.Params();
        p.enabled = opt(c) && (bluetooth || all(c));
        System.arraycopy(curveFor(c, profile(c)), 0, p.eq, 0, BtDsp.BANDS);
        p.targetLufs = TARGET_LUFS[boost(c)];
        p.comp = comp(c);
        p.wide = wide(c);
        p.vbass = vbass(c);
        p.ceilingDb = -1.5f;
        return p;
    }

    // ------------------------------------------------------------------ settings (shared with the UI)

    public static boolean pin(Context c) {
        return Store.flag(c, K_PIN, true);
    }

    public static boolean opt(Context c) {
        return Store.flag(c, K_OPT, true);
    }

    public static boolean comp(Context c) {
        return Store.flag(c, K_COMP, true);
    }

    public static boolean wide(Context c) {
        return Store.flag(c, K_WIDE, false);
    }

    public static boolean vbass(Context c) {
        return Store.flag(c, K_VBASS, true);
    }

    public static boolean all(Context c) {
        return Store.flag(c, K_ALL, false);
    }

    public static boolean autoPlay(Context c) {
        return Store.flag(c, K_AUTO, false);
    }

    /** Kept for the UI: the engine is plain Java now, so it works on every supported Android version. */
    public static boolean optSupported() {
        return true;
    }

    public static int profile(Context c) {
        return Math.max(0, Math.min(PROFILES - 1, Store.prefs(c).getInt(K_PROFILE, 0)));
    }

    public static int boost(Context c) {
        return Math.max(0, Math.min(TARGET_LUFS.length - 1, Store.prefs(c).getInt(K_BOOST, 1)));
    }

    public static void setInt(Context c, String k, int v) {
        Store.prefs(c).edit().putInt(k, v).apply();
    }

    public static boolean isKey(String k) {
        return K_PIN.equals(k) || K_OPT.equals(k) || K_PROFILE.equals(k) || K_BOOST.equals(k) || K_COMP.equals(k)
                || K_WIDE.equals(k) || K_AUTO.equals(k) || K_EQ.equals(k) || K_VBASS.equals(k) || K_ALL.equals(k);
    }

    /** The user's own curve (dB per band); starts as a copy of the built-in one that was selected. */
    public static float[] customCurve(Context c) {
        float[] out = new float[BtDsp.BANDS];
        String s = Store.prefs(c).getString(K_EQ, "");
        if (s == null || s.isEmpty()) {
            int p = profile(c);
            if (p < CURVES.length) System.arraycopy(CURVES[p], 0, out, 0, out.length);
            return out;
        }
        String[] a = s.split(",");
        for (int i = 0; i < out.length && i < a.length; i++) {
            try {
                out[i] = Math.max(-8f, Math.min(8f, Float.parseFloat(a[i])));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static void saveCustom(Context c, float[] g) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < g.length; i++) sb.append(i > 0 ? "," : "").append(String.format(java.util.Locale.US, "%.1f", g[i]));
        Store.prefs(c).edit().putString(K_EQ, sb.toString()).apply();
    }

    static float[] curveFor(Context c, int p) {
        return p == CUSTOM ? customCurve(c) : CURVES[p];
    }

    // ------------------------------------------------------------------ outputs

    static boolean isBt(int t) {
        return t == 7 || t == 8 || t == 23 || t == 26 || t == 27 || t == 30;   // SCO, A2DP, hearing aid, LE headset / speaker / broadcast
    }

    /** All connected Bluetooth outputs, music-capable ones first (calls-only SCO last). */
    public static List<AudioDeviceInfo> outputs(Context c) {
        List<AudioDeviceInfo> l = new ArrayList<>();
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return l;
        try {
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (isBt(d.getType())) l.add(d);
            }
        } catch (Exception ignored) {
        }
        l.sort((a, b) -> Integer.compare(a.getType() == 7 ? 1 : 0, b.getType() == 7 ? 1 : 0));
        return l;
    }

    /** The output used for music: first non-SCO Bluetooth device. */
    public static AudioDeviceInfo primary(Context c) {
        for (AudioDeviceInfo d : outputs(c)) if (d.getType() != 7) return d;
        return null;
    }

    public static boolean connected(Context c) {
        return !outputs(c).isEmpty();
    }
}
