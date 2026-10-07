package com.simomusic.player;

import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * Foreground media-playback service. Media3 posts the media notification (also shown on the lock screen) and
 * keeps the service in the foreground while playing. On top of that, when the screen turns on while music is
 * playing and the device is locked, the custom lock-screen player is shown over the keyguard.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerService extends MediaSessionService {
    private static final int LOCK_ID = 4401;

    private MediaSession session;
    private ExoPlayer player;
    private final Handler h = new Handler(Looper.getMainLooper());

    private int tries;

    private boolean wantsLock() {
        return player != null && player.getMediaItemCount() > 0 && player.getPlayWhenReady()
                && player.getPlaybackState() != Player.STATE_ENDED;
    }

    /** Right after the screen wakes the keyguard flag can lag a moment (Xiaomi / Samsung), so retry briefly. */
    private final Runnable showLockRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (!wantsLock()) return;
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null && km.isKeyguardLocked()) {
                    LockLauncher.show(PlayerService.this);
                } else if (++tries < 10) {
                    h.postDelayed(this, 150);
                }
            } catch (RuntimeException e) {
                CrashGuard.nonFatal("show lock player", e);
            }
        }
    };

    /** Optional mode: a moment after you lock the phone, light the screen again with the player. */
    private final Runnable wakeAfterLock = new Runnable() {
        @Override
        public void run() {
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (wantsLock() && pm != null && !pm.isInteractive()) LockLauncher.show(PlayerService.this);
            } catch (RuntimeException e) {
                CrashGuard.nonFatal("wake after lock", e);
            }
        }
    };

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
          try {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(a)) {
                h.removeCallbacks(wakeAfterLock);
                h.removeCallbacks(showLockRunnable);
                tries = 0;
                if (Store.flag(c, "lock_auto", true)) h.post(showLockRunnable);
            } else if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                h.removeCallbacks(showLockRunnable);
                LockLauncher.clear(c);
                boolean justClosedLock = android.os.SystemClock.elapsedRealtime() - LockLauncher.lastSeen < 4000;
                if (Store.flag(c, "lock_wake", false) && !justClosedLock) h.postDelayed(wakeAfterLock, 1500);
            } else {
                LockLauncher.clear(c);
            }
          } catch (RuntimeException e) {
              CrashGuard.nonFatal("screen event", e);
          }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefs = (sp, key) -> {
        if (BtAudio.isKey(key)) BtAudio.refresh();
        if ("skip_silence".equals(key) && player != null) player.setSkipSilenceEnabled(sp.getBoolean(key, false));
        if ("lock_auto".equals(key) && player != null) OverlayAnchor.sync(this, player.getPlayWhenReady() && sp.getBoolean(key, true));
    };

    private final android.os.Handler saveTick = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable saveRun = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                Resume.save(PlayerService.this, player);
                saveTick.postDelayed(this, 15000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this, new androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            @Override
            protected androidx.media3.exoplayer.audio.AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                                                                               boolean enableAudioTrackPlaybackParams) {
                // the sound engine runs right before the AudioTrack, so everything Android sends to the headset has passed it
                return new androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(new androidx.media3.common.audio.AudioProcessor[]{BtDspProcessor.INSTANCE})
                        .build();
            }
        })
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        player.setSkipSilenceEnabled(Store.flag(this, "skip_silence", false));
        BtAudio.attach(this, player);
        GroupAudio.attach(this, player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                OverlayAnchor.sync(PlayerService.this, playWhenReady && Store.flag(PlayerService.this, "lock_auto", true));
            }

            @Override
            public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) {
                Resume.save(PlayerService.this, player);
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                Resume.save(PlayerService.this, player);
                saveTick.removeCallbacksAndMessages(null);
                if (playing) saveTick.postDelayed(saveRun, 15000);
            }

            @Override
            public void onTimelineChanged(androidx.media3.common.Timeline t, int reason) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) Resume.save(PlayerService.this, player);
            }
        });
        // tapping the media notification opens the app itself with the full player up, so back / close land in the app
        PendingIntent open = PendingIntent.getActivity(this, 0, MainActivity.openPlayerIntent(this),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, player).setSessionActivity(open).setBitmapLoader(new ArtLoader(this)).build();
        DefaultMediaNotificationProvider np = new DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.ch_playback).build();
        np.setSmallIcon(R.drawable.ic_notif);
        setMediaNotificationProvider(np);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        ContextCompat.registerReceiver(this, screen, f, ContextCompat.RECEIVER_NOT_EXPORTED);
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefs);
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo info) {
        return session;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (session == null) {
            stopSelf();
            return;
        }
        Player p = session.getPlayer();
        if (!p.getPlayWhenReady() || p.getMediaItemCount() == 0 || p.getPlaybackState() == Player.STATE_ENDED) stopSelf();
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screen);
        } catch (Exception ignored) {
        }
        OverlayAnchor.sync(this, false);
        saveTick.removeCallbacksAndMessages(null);
        if (player != null) Resume.save(this, player);
        GroupAudio.detach();
        BtAudio.detach();
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefs);
        h.removeCallbacksAndMessages(null);
        if (session != null) session.release();
        if (player != null) player.release();
        session = null;
        player = null;
        super.onDestroy();
    }
}
