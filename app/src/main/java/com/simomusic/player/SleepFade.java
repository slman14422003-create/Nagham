package com.simomusic.player;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

/**
 * Sleep timer comfort: the last 15 seconds before the timer pauses the music, the volume glides down to silence
 * instead of cutting off, and goes back to normal right after the pause (or at once if the timer is cancelled).
 */
public final class SleepFade {
    private SleepFade() {
    }

    private static final long FADE_MS = 15000, STEP_MS = 400;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Runnable step;
    private static long fadeStart;

    /** Called when a timer is set: the fade begins {@code totalMs - 15 s} from now. */
    public static void start(long totalMs) {
        cancel();
        fadeStart = SystemClock.elapsedRealtime() + Math.max(0, totalMs - FADE_MS);
        step = new Runnable() {
            @Override
            public void run() {
                MediaController m = Pb.get();
                if (m == null || step != this) return;
                long into = SystemClock.elapsedRealtime() - fadeStart;
                if (into >= 0 && m.isCommandAvailable(Player.COMMAND_SET_VOLUME)) {
                    m.setVolume(Math.max(0f, 1f - into / (float) FADE_MS));
                }
                H.postDelayed(this, into < 0 ? Math.min(60000L, -into) : STEP_MS);
            }
        };
        H.post(step);
    }

    /** The timer was cancelled or replaced: stop fading and bring the volume back. */
    public static void cancel() {
        step = null;
        H.removeCallbacksAndMessages(null);
        restore();
    }

    /** The timer fired and paused the music: the volume comes back a moment later, for the next time you press play. */
    public static void finish() {
        step = null;
        H.removeCallbacksAndMessages(null);
        H.postDelayed(SleepFade::restore, 600);
    }

    private static void restore() {
        try {
            MediaController m = Pb.get();
            if (m != null && m.isCommandAvailable(Player.COMMAND_SET_VOLUME) && m.getVolume() < 1f) m.setVolume(1f);
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("sleep fade restore", e);
        }
    }
}
