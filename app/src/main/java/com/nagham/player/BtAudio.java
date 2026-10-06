package com.nagham.player;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.DynamicsProcessing;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Bluetooth audio output manager (lives with the player service).
 * <ul>
 * <li>Keeps playback on the connected headset (preferred output device) when "keep sound on the headset" is on.</li>
 * <li>While a headset is the output, runs the music through a gentle compressor + limiter before it is sent,
 * so the lossy Bluetooth codec gets a clean, even signal (quiet parts stay clear, loud parts do not clip).</li>
 * <li>Reacts to headsets connecting / disconnecting (debounced), re-creating the effect when the audio session changes.</li>
 * </ul>
 * The Bluetooth codec itself (SBC / AAC / LDAC / aptX) is negotiated by Android and the headset; apps cannot force it.
 */
@OptIn(markerClass = UnstableApi.class)
public final class BtAudio {
    public static final String K_PIN = "bt_pin", K_OPT = "bt_opt";

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
            if (android.os.SystemClock.uptimeMillis() - startedAt < 2500 || !autoPlay(app)) return;
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

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onAudioSessionIdChanged(int audioSessionId) {
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

    /** Settings changed: re-evaluate routing and the effect. */
    public static void refresh() {
        if (inst != null) inst.schedule();
    }

    private void start() {
        startedAt = android.os.SystemClock.uptimeMillis();
        if (am != null) am.registerAudioDeviceCallback(devices, h);
        player.addListener(listener);
        schedule();
    }

    private void stop() {
        h.removeCallbacks(applyRun);
        try {
            if (am != null) am.unregisterAudioDeviceCallback(devices);
        } catch (Exception ignored) {
        }
        try {
            player.removeListener(listener);
            player.setPreferredAudioDevice(null);
        } catch (Exception ignored) {
        }
        release();
    }

    /** Headsets often report several events in a row while connecting; handle them once. */
    private void schedule() {
        h.removeCallbacks(applyRun);
        h.postDelayed(applyRun, 250);
    }

    private void applyNow() {
        AudioDeviceInfo target = primary(app);
        try {
            AudioDeviceInfo want = (target != null && pin(app)) ? target : null;
            int wantId = want == null ? -1 : want.getId();
            if (wantId != pinnedId) {
                player.setPreferredAudioDevice(want);
                pinnedId = wantId;
            }
        } catch (Exception ignored) {
        }
        updateFx(target != null && opt(app));
    }

    // ------------------------------------------------------------------ sound tuning (EQ + loudness + compressor + limiter)

    public static final String K_PROFILE = "bt_profile", K_BOOST = "bt_boost", K_COMP = "bt_comp",
            K_WIDE = "bt_wide", K_AUTO = "bt_auto", K_EQ = "bt_eq";
    /** Profiles 0-4 are built in, 5 is the user's own 10-band curve. */
    public static final int PROFILES = 6, CUSTOM = 5;

    /** Center frequencies of the 10 tuning bands (Hz). */
    static final float[] FREQ = {31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f};

    /**
     * Gain per band in dB. 0 = "Auto for budget TWS": cheap earbuds are usually thin in the sub-bass, muddy around
     * 250 Hz and harsh / sibilant in the upper treble, so this curve adds bass, clears the mud, lifts the voice
     * range a touch and softens 4-16 kHz.
     */
    static final float[][] CURVES = {
            {3.0f, 3.5f, 2.0f, -1.5f, -0.5f, 0f, 1.0f, -1.5f, -2.5f, -1.0f},   // Auto: budget TWS
            {1.0f, 1.0f, 0.5f, -0.5f, 0f, 0f, 0.5f, 0f, -1.0f, 0f},            // Balanced
            {5.0f, 5.0f, 3.0f, 1.0f, 0f, 0f, 0f, -0.5f, -1.0f, 0f},            // Bass
            {-1.0f, -1.0f, -0.5f, 0f, 1.0f, 2.0f, 2.5f, 1.0f, 0f, 0f},         // Vocal
            {0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f}                           // Flat
    };
    static final float[] BOOST_DB = {0f, 3f, 6f, 9f};

    private final java.util.List<AudioEffect> fxs = new java.util.ArrayList<>();
    private String fxSig;

    public static int profile(Context c) {
        return Math.max(0, Math.min(PROFILES - 1, Store.prefs(c).getInt(K_PROFILE, 0)));
    }

    public static int boost(Context c) {
        return Math.max(0, Math.min(BOOST_DB.length - 1, Store.prefs(c).getInt(K_BOOST, 1)));
    }

    public static boolean comp(Context c) {
        return Store.flag(c, K_COMP, true);
    }

    public static void setInt(Context c, String k, int v) {
        Store.prefs(c).edit().putInt(k, v).apply();
    }

    public static boolean isKey(String k) {
        return K_PIN.equals(k) || K_OPT.equals(k) || K_PROFILE.equals(k) || K_BOOST.equals(k) || K_COMP.equals(k)
                || K_WIDE.equals(k) || K_AUTO.equals(k) || K_EQ.equals(k);
    }

    public static boolean wide(Context c) {
        return Store.flag(c, K_WIDE, false);
    }

    public static boolean autoPlay(Context c) {
        return Store.flag(c, K_AUTO, false);
    }

    /** The user's own curve (dB per band); starts as a copy of the built-in one that was selected. */
    public static float[] customCurve(Context c) {
        float[] out = new float[FREQ.length];
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

    private static float gainAt(float[] curve, float hz) {
        if (hz <= FREQ[0]) return curve[0];
        for (int i = 1; i < FREQ.length; i++) {
            if (hz <= FREQ[i]) {
                float t = (float) (Math.log(hz / FREQ[i - 1]) / Math.log(FREQ[i] / FREQ[i - 1]));
                return curve[i - 1] + (curve[i] - curve[i - 1]) * t;
            }
        }
        return curve[curve.length - 1];
    }

    private void updateFx(boolean want) {
        int sid = player.getAudioSessionId();
        if (!want || sid == C.AUDIO_SESSION_ID_UNSET || sid == 0) {
            release();
            return;
        }
        final float[] curve = curveFor(app, profile(app));
        String sig = sid + "|" + java.util.Arrays.toString(curve) + "|" + boost(app) + "|" + comp(app) + "|" + wide(app);
        if (sig.equals(fxSig) && !fxs.isEmpty()) {
            try {
                for (AudioEffect e : fxs) if (!e.getEnabled()) e.setEnabled(true);
                return;
            } catch (Exception e) {
                // fall through and rebuild
            }
        }
        release();
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                fxs.add(buildDynamics(sid, curve, BOOST_DB[boost(app)], comp(app)));
            } else {
                buildLegacy(sid, curve, BOOST_DB[boost(app)]);
            }
            if (wide(app)) {
                try {
                    // cheap earbuds have a narrow, "inside the head" image; this widens it a little
                    android.media.audiofx.Virtualizer v = new android.media.audiofx.Virtualizer(0, sid);
                    if (v.getStrengthSupported()) v.setStrength((short) 600);
                    fxs.add(v);
                } catch (Throwable ignored) {
                }
            }
            for (AudioEffect e : fxs) e.setEnabled(true);
            fxSig = sig;
        } catch (Throwable t) {
            release();
        }
    }

    /** Android 9+: one effect = 10-band EQ -> input gain -> compressor -> limiter. */
    @RequiresApi(28)
    private static AudioEffect buildDynamics(int sid, float[] curve, float boostDb, boolean comp) {
        final int bands = FREQ.length;
        DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION, 2,
                true, bands, comp, comp ? 1 : 0, false, 0, true).build();
        DynamicsProcessing dp = new DynamicsProcessing(0, sid, cfg);
        dp.setPreEqAllChannelsTo(new DynamicsProcessing.Eq(true, true, bands));
        float maxUp = 0f;
        for (int i = 0; i < bands; i++) {
            float edge = i == bands - 1 ? 20000f : (float) Math.sqrt(FREQ[i] * FREQ[i + 1]);
            dp.setPreEqBandAllChannelsTo(i, new DynamicsProcessing.EqBand(true, edge, curve[i]));
            maxUp = Math.max(maxUp, curve[i]);
        }
        // headroom: the louder the EQ lifts, the less input gain, so the limiter only catches rare peaks
        dp.setInputGainAllChannelsTo(boostDb - maxUp * 0.5f);
        if (comp) {
            dp.setMbcAllChannelsTo(new DynamicsProcessing.Mbc(true, true, 1));
            dp.setMbcBandAllChannelsTo(0, new DynamicsProcessing.MbcBand(true, 20000f, 12f, 160f, 2.2f, -22f, 8f, -80f, 1f, 0f, 2.0f));
        }
        dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1.5f, 0f));
        return dp;
    }

    /** Android 7-8: system equalizer + loudness enhancer (no compressor / limiter, so the boost is capped). */
    private void buildLegacy(int sid, float[] curve, float boostDb) {
        android.media.audiofx.Equalizer eq = new android.media.audiofx.Equalizer(0, sid);
        fxs.add(eq);
        short[] range = eq.getBandLevelRange();
        for (short b = 0; b < eq.getNumberOfBands(); b++) {
            float hz = eq.getCenterFreq(b) / 1000f;
            int mb = Math.round(gainAt(curve, hz) * 100f);
            eq.setBandLevel(b, (short) Math.max(range[0], Math.min(range[1], mb)));
        }
        if (boostDb > 0f) {
            android.media.audiofx.LoudnessEnhancer le = new android.media.audiofx.LoudnessEnhancer(sid);
            le.setTargetGain(Math.round(Math.min(boostDb, 6f) * 100f));
            fxs.add(le);
        }
    }

    private void release() {
        for (AudioEffect e : fxs) {
            try {
                e.release();
            } catch (Exception ignored) {
            }
        }
        fxs.clear();
        fxSig = null;
    }

    // ------------------------------------------------------------------ helpers shared with the UI

    public static boolean pin(Context c) {
        return Store.flag(c, K_PIN, true);
    }

    public static boolean opt(Context c) {
        return Store.flag(c, K_OPT, true);
    }

    /** The compressor / limiter needs Android 9+; EQ and loudness work on every supported version. */
    public static boolean optSupported() {
        return Build.VERSION.SDK_INT >= 28;
    }

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
