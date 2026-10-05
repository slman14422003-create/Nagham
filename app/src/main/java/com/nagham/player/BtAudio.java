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
    private AudioEffect fx;
    private int fxSession = -1;
    private int pinnedId = -1;

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

    // ------------------------------------------------------------------ compressor / limiter

    private void updateFx(boolean want) {
        if (Build.VERSION.SDK_INT < 28) return;
        int sid = player.getAudioSessionId();
        if (!want || sid == C.AUDIO_SESSION_ID_UNSET || sid == 0) {
            release();
            return;
        }
        if (fx != null && fxSession == sid) {
            try {
                if (!fx.getEnabled()) fx.setEnabled(true);
                return;
            } catch (Exception e) {
                release();
            }
        }
        release();
        try {
            AudioEffect e = build(sid);
            e.setEnabled(true);
            fx = e;
            fxSession = sid;
        } catch (Throwable t) {
            fx = null;
            fxSession = -1;
        }
    }

    @RequiresApi(28)
    private static AudioEffect build(int sid) {
        DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION, 2,
                false, 0, true, 1, false, 0, true).build();
        DynamicsProcessing dp = new DynamicsProcessing(0, sid, cfg);
        dp.setMbcAllChannelsTo(new DynamicsProcessing.Mbc(true, true, 1));
        // one wide band, 2.2:1 above -22 dB with a soft knee, a little make-up gain
        dp.setMbcBandAllChannelsTo(0, new DynamicsProcessing.MbcBand(true, 20000f, 12f, 160f, 2.2f, -22f, 8f, -80f, 1f, 0f, 3.5f));
        // brick-wall safety so the make-up gain never clips before the codec
        dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1.5f, 0f));
        return dp;
    }

    private void release() {
        if (fx != null) {
            try {
                fx.release();
            } catch (Exception ignored) {
            }
        }
        fx = null;
        fxSession = -1;
    }

    // ------------------------------------------------------------------ helpers shared with the UI

    public static boolean pin(Context c) {
        return Store.flag(c, K_PIN, true);
    }

    public static boolean opt(Context c) {
        return Store.flag(c, K_OPT, true);
    }

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
