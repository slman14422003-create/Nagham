package com.simomusic.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.media3.session.MediaController;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Song menu, add-to-playlist, sort, sleep timer and the name prompt. */
public final class Menus {
    private Menus() {
    }

    public static void song(final Context c, final Track t, final Runnable changed) {
        String sub = t.artist == null || t.artist.isEmpty() ? t.album : t.artist;
        if (sub == null || sub.isEmpty()) sub = t.ext.toUpperCase();
        Sheet.show(c, t.title, sub, () -> {
            List<Sheet.Item> l = new ArrayList<>();
            l.add(Sheet.item(R.drawable.ic_play, c.getString(R.string.play_next), false, false, () -> {
                Pb.next(c, t);
                Ui.toast(c, R.string.added_queue);
            }));
            l.add(Sheet.item(R.drawable.ic_list, c.getString(R.string.add_to_queue), false, false, () -> {
                Pb.enqueue(c, t);
                Ui.toast(c, R.string.added_queue);
            }));
            l.add(Sheet.divider());
            l.add(Sheet.item(R.drawable.ic_add, c.getString(R.string.add_to_playlist), false, false, () -> addToPlaylist(c, t.id, changed)));
            boolean fav = Store.has(c, Store.FAV, t.id);
            l.add(Sheet.item(fav ? R.drawable.ic_heart_fill : R.drawable.ic_heart,
                    c.getString(fav ? R.string.unfavorite : R.string.favorite), fav, false, () -> {
                        Store.toggle(c, Store.FAV, t.id);
                        if (changed != null) changed.run();
                    }));
            l.add(Sheet.divider());
            l.add(Sheet.item(R.drawable.ic_share, c.getString(R.string.share), false, false, () -> SongInfo.share(c, t)));
            l.add(Sheet.item(R.drawable.ic_info, c.getString(R.string.song_info), false, false, () -> SongInfo.show(c, t)));
            l.add(Sheet.item(R.drawable.ic_ring, c.getString(R.string.set_ringtone), false, false, () -> RingtoneActivity.start(c, t)));
            l.add(Sheet.divider());
            l.add(Sheet.danger(R.drawable.ic_delete, c.getString(R.string.delete_song), () ->
                    Dlg.confirm(c, t.title, c.getString(R.string.confirm_delete_song), R.string.delete, true, () -> DeleteActivity.start(c, t))));
            return l;
        });
    }

    /** One tap toggles the track in a playlist and the sheet stays open, so several lists can be set quickly. */
    public static void addToPlaylist(final Context c, final long trackId, final Runnable changed) {
        Sheet.show(c, c.getString(R.string.add_to_playlist), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            l.add(Sheet.item(R.drawable.ic_add, c.getString(R.string.new_playlist), false, false, () ->
                    ask(c, R.string.new_playlist, "", name -> {
                        Store.Playlist p = Store.create(c, name);
                        Store.toggle(c, p.id, trackId);
                        if (changed != null) changed.run();
                    })));
            for (final Store.Playlist p : Store.playlists(c)) {
                boolean in = p.ids.contains(trackId);
                l.add(Sheet.item(Store.FAV.equals(p.id) ? R.drawable.ic_heart : R.drawable.ic_list, p.name, in, true, () -> {
                    Store.toggle(c, p.id, trackId);
                    if (changed != null) changed.run();
                }));
            }
            return l;
        });
    }

    /** The play queue: reorder, remove, jump. */
    public static void queue(final Context c) {
        QueueSheet.show(c);
    }

    public static void sort(final Context c, final Runnable changed) {
        final int[] names = {R.string.sort_title, R.string.sort_artist, R.string.sort_recent, R.string.sort_duration};
        Sheet.show(c, c.getString(R.string.sort_by), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            CharSequence[] labels = new CharSequence[names.length];
            for (int i = 0; i < names.length; i++) labels[i] = c.getString(names[i]);
            l.add(Sheet.chips(labels, Store.sort(c), 2, m -> {
                Store.setSort(c, m);
                changed.run();
            }));
            return l;
        });
    }

    public static void sleep(final Context c) {
        sleep(c, null);
    }

    public static void sleep(final Context c, final Runnable changed) {
        final int[] mins = {15, 30, 45, 60, 90};
        Sheet.show(c, c.getString(R.string.sleep_timer), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            CharSequence[] labels = new CharSequence[mins.length];
            for (int i = 0; i < mins.length; i++) labels[i] = c.getString(R.string.sleep_chip, mins[i]);
            l.add(Sheet.chips(labels, -1, 3, i -> {
                Pb.sleep(mins[i]);
                if (changed != null) changed.run();
                Ui.toast(c, c.getString(R.string.sleep_set, mins[i]));
            }));
            if (Pb.sleepAt > 0) {
                l.add(Sheet.divider());
                l.add(Sheet.danger(R.drawable.ic_close, c.getString(R.string.sleep_off), () -> {
                    Pb.sleep(0);
                    if (changed != null) changed.run();
                }));
            }
            return l;
        });
    }

    private static String speedLabel(float s) {
        return (s == (int) s ? String.valueOf((int) s) : String.valueOf(s)) + "×";
    }

    /** Player "more" menu: playback speed, sleep timer (shows what is left), lock-screen preview. */
    public static void more(final Context c, final Runnable changed) {
        Sheet.show(c, c.getString(R.string.more), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            MediaController m = Pb.get();
            float sp = m == null ? 1f : m.getPlaybackParameters().speed;
            l.add(Sheet.item(R.drawable.ic_play, c.getString(R.string.playback_speed) + " · " + speedLabel(sp), sp != 1f, false,
                    () -> speed(c)));
            boolean on = Pb.sleepAt > 0;
            long left = on ? Math.max(1, (Pb.sleepAt - SystemClock.elapsedRealtime() + 59999) / 60000) : 0;
            l.add(Sheet.item(R.drawable.ic_timer, on ? c.getString(R.string.sleep_left, (int) left) : c.getString(R.string.sleep_timer),
                    on, false, () -> sleep(c, changed)));
            l.add(Sheet.item(R.drawable.ic_lock, c.getString(R.string.lock_preview), false, false, () -> {
                if (c instanceof Activity) openLock((Activity) c);
            }));
            Track cur = Library.byId.get(Pb.currentId());
            if (cur != null) {
                l.add(Sheet.item(R.drawable.ic_ring, c.getString(R.string.set_ringtone), false, false, () -> RingtoneActivity.start(c, cur)));
            }
            return l;
        });
    }

    public static void speed(final Context c) {
        final float[] opts = {0.75f, 1f, 1.25f, 1.5f, 2f};
        Sheet.show(c, c.getString(R.string.playback_speed), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            MediaController m = Pb.get();
            float cur = m == null ? 1f : m.getPlaybackParameters().speed;
            CharSequence[] labels = new CharSequence[opts.length];
            int sel = -1;
            for (int i = 0; i < opts.length; i++) {
                labels[i] = speedLabel(opts[i]);
                if (Math.abs(cur - opts[i]) < 0.01f) sel = i;
            }
            l.add(Sheet.chips(labels, sel, 3, i -> {
                MediaController mm = Pb.get();
                if (mm != null) mm.setPlaybackSpeed(opts[i]);
            }));
            return l;
        });
    }

    public static void ask(Context c, int titleRes, String initial, final Consumer<String> ok) {
        Dlg.input(c, titleRes, initial, R.string.save, ok);
    }

    public static void openLock(Activity a) {
        Ui.go(a, new Intent(a, LockActivity.class));
    }
}
