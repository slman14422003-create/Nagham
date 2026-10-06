package com.nagham.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Settings + playlists, kept in SharedPreferences (playlists as JSON). */
public final class Store {
    private Store() {
    }

    /** Format keys shown in Settings, and the file extensions each one covers. */
    public static final String[] FORMATS = {"mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "amr", "mka"};
    private static final String[][] EXT = {{"mp3"}, {"flac"}, {"wav", "wave"}, {"m4a", "m4b", "mp4"},
            {"aac", "adts"}, {"ogg", "oga"}, {"opus"}, {"amr", "awb"}, {"mka"}};

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("nagham", Context.MODE_PRIVATE);
    }

    public static boolean flag(Context c, String k, boolean def) {
        return prefs(c).getBoolean(k, def);
    }

    public static void setFlag(Context c, String k, boolean v) {
        prefs(c).edit().putBoolean(k, v).apply();
    }

    public static boolean formatOn(Context c, String f) {
        return prefs(c).getBoolean("fmt_" + f, true);
    }

    public static void setFormatOn(Context c, String f, boolean on) {
        prefs(c).edit().putBoolean("fmt_" + f, on).apply();
    }

    public static Set<String> enabledExts(Context c) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < FORMATS.length; i++) {
            if (formatOn(c, FORMATS[i])) for (String e : EXT[i]) out.add(e);
        }
        return out;
    }

    /** Files shorter than this many seconds are hidden (0 = show everything). */
    public static int minDur(Context c) {
        return prefs(c).getInt("min_dur", 0);
    }

    public static void setMinDur(Context c, int s) {
        prefs(c).edit().putInt("min_dur", s).apply();
    }

    public static int sort(Context c) {
        return prefs(c).getInt("sort", 0);
    }

    public static void setSort(Context c, int s) {
        prefs(c).edit().putInt("sort", s).apply();
    }

    // ------------------------------------------------------------------ playlists

    public static final String FAV = "fav";

    public static final class Playlist {
        public String id, name;
        public final List<Long> ids = new ArrayList<>();
    }

    private static List<Playlist> cache;

    public static synchronized List<Playlist> playlists(Context c) {
        if (cache == null) load(c);
        return cache;
    }

    private static void load(Context c) {
        cache = new ArrayList<>();
        String s = prefs(c).getString("playlists", null);
        try {
            JSONArray a = s == null ? new JSONArray() : new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Playlist p = new Playlist();
                p.id = o.getString("id");
                p.name = o.getString("name");
                JSONArray ids = o.getJSONArray("ids");
                for (int j = 0; j < ids.length(); j++) p.ids.add(ids.getLong(j));
                cache.add(p);
            }
        } catch (JSONException ignored) {
        }
        boolean hasFav = false;
        for (Playlist p : cache) if (FAV.equals(p.id)) hasFav = true;
        if (!hasFav) {
            Playlist f = new Playlist();
            f.id = FAV;
            f.name = c.getString(R.string.favorites);
            cache.add(0, f);
        }
    }

    private static void save(Context c) {
        JSONArray a = new JSONArray();
        try {
            for (Playlist p : cache) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                JSONArray ids = new JSONArray();
                for (long id : p.ids) ids.put(id);
                o.put("ids", ids);
                a.put(o);
            }
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString("playlists", a.toString()).apply();
    }

    public static synchronized Playlist playlist(Context c, String id) {
        for (Playlist p : playlists(c)) if (p.id.equals(id)) return p;
        return null;
    }

    public static synchronized Playlist create(Context c, String name) {
        Playlist p = new Playlist();
        p.id = "p" + System.currentTimeMillis();
        p.name = name;
        playlists(c).add(p);
        save(c);
        return p;
    }

    public static synchronized void rename(Context c, String id, String name) {
        Playlist p = playlist(c, id);
        if (p != null && !FAV.equals(id)) {
            p.name = name;
            save(c);
        }
    }

    /** Removes deleted songs from favourites and every playlist. */
    public static synchronized void forget(Context c, java.util.Collection<Long> gone) {
        boolean any = false;
        for (Playlist p : playlists(c)) any |= p.ids.removeAll(gone);
        if (any) save(c);
    }

    public static synchronized void delete(Context c, String id) {
        if (FAV.equals(id)) return;
        Playlist p = playlist(c, id);
        if (p != null) {
            cache.remove(p);
            save(c);
        }
    }

    public static synchronized boolean has(Context c, String pid, long tid) {
        Playlist p = playlist(c, pid);
        return p != null && p.ids.contains(tid);
    }

    /** Adds or removes the track; returns true when it is in the playlist afterwards. */
    public static synchronized boolean toggle(Context c, String pid, long tid) {
        Playlist p = playlist(c, pid);
        if (p == null) return false;
        boolean in = p.ids.contains(tid);
        if (in) p.ids.remove(Long.valueOf(tid));
        else p.ids.add(tid);
        save(c);
        return !in;
    }

    public static synchronized int addAll(Context c, String pid, Collection<Long> ids) {
        Playlist p = playlist(c, pid);
        if (p == null) return 0;
        int n = 0;
        for (long id : ids) {
            if (!p.ids.contains(id)) {
                p.ids.add(id);
                n++;
            }
        }
        save(c);
        return n;
    }

    public static synchronized void setOrder(Context c, String pid, List<Long> ids) {
        Playlist p = playlist(c, pid);
        if (p == null) return;
        p.ids.clear();
        p.ids.addAll(ids);
        save(c);
    }
}
