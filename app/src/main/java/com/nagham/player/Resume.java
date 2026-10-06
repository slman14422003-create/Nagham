package com.nagham.player;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.media3.common.Player;

import java.util.ArrayList;
import java.util.List;

/** Remembers the queue, current song and position, and puts them back (paused) when the app is opened again. */
public final class Resume {
    private Resume() {
    }

    private static boolean restored;
    private static final int MAX = 1000;

    /** Called from the player service; cheap enough to call on every song change / pause. */
    public static void save(Context c, Player p) {
        int n = p.getMediaItemCount();
        if (n == 0) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, MAX); i++) sb.append(p.getMediaItemAt(i).mediaId).append(',');
        androidx.media3.common.MediaItem cur = p.getCurrentMediaItem();
        Store.prefs(c).edit()
                .putString("rs_ids", sb.toString())
                .putString("rs_cur", cur == null ? "" : cur.mediaId)
                .putLong("rs_pos", Math.max(0, p.getCurrentPosition()))
                .putBoolean("rs_shuffle", p.getShuffleModeEnabled())
                .putInt("rs_repeat", p.getRepeatMode())
                .apply();
    }

    /** Once per app start, after the library is loaded. Does nothing if something is already queued. */
    public static void restore(Context c) {
        if (restored || !Store.flag(c, "resume", true)) return;
        restored = true;
        SharedPreferences sp = Store.prefs(c);
        String ids = sp.getString("rs_ids", "");
        if (ids == null || ids.isEmpty()) return;
        String cur = sp.getString("rs_cur", "");
        List<Track> list = new ArrayList<>();
        int idx = 0;
        for (String s : ids.split(",")) {
            if (s.isEmpty()) continue;
            try {
                Track t = Library.byId.get(Long.parseLong(s));
                if (t == null) continue;
                if (s.equals(cur)) idx = list.size();
                list.add(t);
            } catch (NumberFormatException ignored) {
            }
        }
        if (list.isEmpty()) return;
        Pb.restore(c, list, idx, sp.getLong("rs_pos", 0), sp.getBoolean("rs_shuffle", false), sp.getInt("rs_repeat", 0));
    }
}
