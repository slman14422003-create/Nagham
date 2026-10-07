package com.simomusic.player;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** App-wide playback client: one MediaController connected to PlayerService. */
public final class Pb {
    private Pb() {
    }

    private static MediaController ctl;
    private static ListenableFuture<MediaController> fut;
    private static final Set<Player.Listener> LS = new CopyOnWriteArraySet<>();
    private static final List<Runnable> WAIT = new ArrayList<>();
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Runnable sleepTask;
    public static long sleepAt;

    public static void connect(Context c) {
        if (fut != null) return;
        final Context app = c.getApplicationContext();
        SessionToken tok = new SessionToken(app, new ComponentName(app, PlayerService.class));
        final ListenableFuture<MediaController> f = new MediaController.Builder(app, tok)
                .setListener(new MediaController.Listener() {
                    @Override
                    public void onDisconnected(MediaController controller) {
                        ctl = null;
                        fut = null;
                    }
                }).buildAsync();
        fut = f;
        f.addListener(() -> {
            try {
                ctl = f.get();
                for (Player.Listener l : LS) ctl.addListener(l);
                List<Runnable> w = new ArrayList<>(WAIT);
                WAIT.clear();
                for (Runnable r : w) {
                    try {
                        r.run();
                    } catch (RuntimeException ex) {
                        CrashGuard.nonFatal("player callback", ex);
                    }
                }
            } catch (Exception e) {
                fut = null;
                ctl = null;
            }
        }, ContextCompat.getMainExecutor(app));
    }

    @Nullable
    public static MediaController get() {
        if (ctl != null && !ctl.isConnected()) {
            ctl = null;
            fut = null;
        }
        return ctl;
    }

    public static void whenReady(Runnable r) {
        if (ctl != null) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                CrashGuard.nonFatal("player callback", ex);
            }
        } else WAIT.add(r);
    }

    public static void add(Player.Listener l) {
        LS.add(l);
        if (ctl != null) ctl.addListener(l);
    }

    public static void remove(Player.Listener l) {
        LS.remove(l);
        if (ctl != null) ctl.removeListener(l);
    }

    public static MediaItem item(Track t) {
        return new MediaItem.Builder()
                .setMediaId(String.valueOf(t.id))
                .setUri(t.uri)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album)
                        .setArtworkUri(t.uri)
                        .setIsPlayable(true).setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build())
                .build();
    }

    /** Plays one item that is not in the library (a file opened from another app). */
    public static void playItem(Context c, final MediaItem item) {
        connect(c);
        whenReady(() -> {
            ctl.setMediaItem(item);
            ctl.prepare();
            ctl.play();
        });
    }

    /** Takes deleted songs out of the running queue. */
    public static void removeIds(final Set<Long> ids) {
        whenReady(() -> {
            for (int i = ctl.getMediaItemCount() - 1; i >= 0; i--) {
                try {
                    if (ids.contains(Long.parseLong(ctl.getMediaItemAt(i).mediaId))) ctl.removeMediaItem(i);
                } catch (NumberFormatException ignored) {
                }
            }
        });
    }

    public static long currentId() {
        if (ctl == null || ctl.getCurrentMediaItem() == null) return -1;
        try {
            return Long.parseLong(ctl.getCurrentMediaItem().mediaId);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean hasMedia() {
        return ctl != null && ctl.getMediaItemCount() > 0;
    }

    /** Replaces the queue and starts playing. index < 0 with shuffle starts on a random track. */
    /** Puts a saved queue back without starting playback; skipped if something is already queued. */
    public static void restore(Context c, final List<Track> list, final int index, final long pos, final boolean shuffle, final int repeat) {
        if (list.isEmpty()) return;
        connect(c);
        final List<MediaItem> items = new ArrayList<>();
        for (Track t : list) items.add(item(t));
        whenReady(() -> {
            if (ctl.getMediaItemCount() > 0) return;
            ctl.setShuffleModeEnabled(shuffle);
            ctl.setRepeatMode(repeat);
            ctl.setMediaItems(items, Math.max(0, Math.min(index, items.size() - 1)), Math.max(0, pos));
            ctl.prepare();
        });
    }

    public static void play(Context c, final List<Track> list, final int index, final boolean shuffle) {
        if (list.isEmpty()) return;
        connect(c);
        final List<MediaItem> items = new ArrayList<>();
        for (Track t : list) items.add(item(t));
        final int start = index >= 0 ? index : new Random().nextInt(items.size());
        whenReady(() -> {
            ctl.setShuffleModeEnabled(shuffle);
            ctl.setMediaItems(items, start, C.TIME_UNSET);
            ctl.prepare();
            ctl.play();
        });
    }

    public static void next(Context c, Track t) {
        if (!hasMedia()) {
            List<Track> one = new ArrayList<>();
            one.add(t);
            play(c, one, 0, false);
        } else {
            ctl.addMediaItem(ctl.getCurrentMediaItemIndex() + 1, item(t));
        }
    }

    public static void enqueue(Context c, Track t) {
        if (!hasMedia()) next(c, t);
        else ctl.addMediaItem(item(t));
    }

    public static void sleep(int minutes) {
        if (sleepTask != null) H.removeCallbacks(sleepTask);
        sleepTask = null;
        sleepAt = 0;
        if (minutes > 0) {
            sleepAt = SystemClock.elapsedRealtime() + minutes * 60000L;
            sleepTask = () -> {
                if (ctl != null) ctl.pause();
                sleepAt = 0;
            };
            H.postDelayed(sleepTask, minutes * 60000L);
        }
    }
}
