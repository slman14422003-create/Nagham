package com.nagham.player;

import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
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

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!Store.flag(c, "lock_auto", true)) return;
            h.postDelayed(() -> {
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (player != null && player.isPlaying() && km != null && km.isKeyguardLocked()) showLock();
            }, 350);
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefs = (sp, key) -> {
        if ("skip_silence".equals(key) && player != null) player.setSkipSilenceEnabled(sp.getBoolean(key, false));
    };

    @Override
    public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        player.setSkipSilenceEnabled(Store.flag(this, "skip_silence", false));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, PlayerActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, player).setSessionActivity(open).setBitmapLoader(new ArtLoader(this)).build();
        DefaultMediaNotificationProvider np = new DefaultMediaNotificationProvider.Builder(this).build();
        np.setSmallIcon(R.drawable.ic_notif);
        setMediaNotificationProvider(np);
        ContextCompat.registerReceiver(this, screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED);
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefs);
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo info) {
        return session;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Player p = session.getPlayer();
        if (!p.getPlayWhenReady() || p.getMediaItemCount() == 0 || p.getPlaybackState() == Player.STATE_ENDED) stopSelf();
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screenOn);
        } catch (Exception ignored) {
        }
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefs);
        h.removeCallbacksAndMessages(null);
        if (session != null) session.release();
        if (player != null) player.release();
        session = null;
        player = null;
        super.onDestroy();
    }

    /** Full-screen-intent notification: the only way to start an activity over the keyguard from the background. */
    private void showLock() {
        if (!Perms.hasNotif(this) || !Perms.hasFsi(this)) return;
        PendingIntent pi = PendingIntent.getActivity(this, 11,
                new Intent(this, LockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification n = new NotificationCompat.Builder(this, App.CH_LOCK)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.lock_notif_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setSilent(true)
                .setTimeoutAfter(8000)
                .build();
        try {
            NotificationManagerCompat.from(this).notify(LOCK_ID, n);
        } catch (SecurityException ignored) {
        }
    }

    public static void clearLock(Context c) {
        NotificationManagerCompat.from(c).cancel(LOCK_ID);
    }
}
