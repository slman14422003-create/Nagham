package com.nagham.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Song menu, add-to-playlist, sort, sleep timer and the name prompt. */
public final class Menus {
    private Menus() {
    }

    public static void song(final Context c, final Track t, final Runnable changed) {
        Sheet.show(c, t.title, () -> {
            List<Sheet.Item> l = new ArrayList<>();
            l.add(Sheet.item(R.drawable.ic_play, c.getString(R.string.play_next), false, false, () -> {
                Pb.next(c, t);
                Ui.toast(c, R.string.added_queue);
            }));
            l.add(Sheet.item(R.drawable.ic_list, c.getString(R.string.add_to_queue), false, false, () -> {
                Pb.enqueue(c, t);
                Ui.toast(c, R.string.added_queue);
            }));
            l.add(Sheet.item(R.drawable.ic_add, c.getString(R.string.add_to_playlist), false, false, () -> addToPlaylist(c, t.id, changed)));
            boolean fav = Store.has(c, Store.FAV, t.id);
            l.add(Sheet.item(fav ? R.drawable.ic_heart_fill : R.drawable.ic_heart,
                    c.getString(fav ? R.string.unfavorite : R.string.favorite), false, false, () -> {
                        Store.toggle(c, Store.FAV, t.id);
                        if (changed != null) changed.run();
                    }));
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
            for (int i = 0; i < names.length; i++) {
                final int m = i;
                l.add(Sheet.item(R.drawable.ic_sort, c.getString(names[i]), Store.sort(c) == m, false, () -> {
                    Store.setSort(c, m);
                    changed.run();
                }));
            }
            return l;
        });
    }

    public static void sleep(final Context c) {
        Sheet.show(c, c.getString(R.string.sleep_timer), () -> {
            List<Sheet.Item> l = new ArrayList<>();
            for (final int m : new int[]{15, 30, 45, 60, 90}) {
                l.add(Sheet.item(R.drawable.ic_timer, c.getString(R.string.sleep_min, m), false, false, () -> {
                    Pb.sleep(m);
                    Ui.toast(c, c.getString(R.string.sleep_set, m));
                }));
            }
            if (Pb.sleepAt > 0) l.add(Sheet.item(R.drawable.ic_delete, c.getString(R.string.sleep_off), false, false, () -> Pb.sleep(0)));
            return l;
        });
    }

    public static void ask(Context c, int titleRes, String initial, final Consumer<String> ok) {
        final EditText e = Ui.edit(c, c.getString(R.string.playlist_name), initial);
        LinearLayout box = new LinearLayout(c);
        box.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), 0);
        box.addView(e);
        AlertDialog d = new AlertDialog.Builder(c, R.style.AppDialog)
                .setTitle(titleRes).setView(box)
                .setPositiveButton(R.string.save, (x, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) ok.accept(s);
                })
                .setNegativeButton(R.string.cancel, null).create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        e.setSelection(e.getText().length());
    }

    public static void openLock(Activity a) {
        a.startActivity(new Intent(a, LockActivity.class));
    }
}
