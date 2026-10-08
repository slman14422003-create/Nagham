package com.simomusic.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Local, on-device listening history that powers the AI Mix. Only ever written while the AI feature is turned on
 * in Settings; turning it off wipes it (see {@link Store#setAiEnabled}). Nothing here ever leaves the phone -
 * it is plain SharedPreferences, read only by {@link AiEngine}.
 */
public final class PlayStats {
    private PlayStats() {
    }

    public static final class Row {
        public int plays, skips;
        public long lastPlayed; // epoch seconds
    }

    private static Map<Long, Row> cache;
    private static int[] hours; // 24 buckets: how often music was started around each hour of day

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("simomusic_stats", Context.MODE_PRIVATE);
    }

    private static synchronized void load(Context c) {
        if (cache != null) return;
        cache = new HashMap<>();
        hours = new int[24];
        SharedPreferences p = prefs(c);
        try {
            JSONObject root = new JSONObject(p.getString("rows", "{}"));
            Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray a = root.getJSONArray(k);
                Row r = new Row();
                r.plays = a.getInt(0);
                r.skips = a.getInt(1);
                r.lastPlayed = a.getLong(2);
                cache.put(Long.parseLong(k), r);
            }
        } catch (JSONException | NumberFormatException ignored) {
        }
        try {
            JSONArray h = new JSONArray(p.getString("hours", "[]"));
            for (int i = 0; i < h.length() && i < 24; i++) hours[i] = h.getInt(i);
        } catch (JSONException ignored) {
        }
    }

    private static void save(Context c) {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<Long, Row> e : cache.entrySet()) {
                JSONArray a = new JSONArray();
                a.put(e.getValue().plays);
                a.put(e.getValue().skips);
                a.put(e.getValue().lastPlayed);
                root.put(String.valueOf(e.getKey()), a);
            }
        } catch (JSONException ignored) {
        }
        JSONArray h = new JSONArray();
        for (int v : hours) h.put(v);
        prefs(c).edit().putString("rows", root.toString()).putString("hours", h.toString()).apply();
    }

    private static Row row(long id) {
        Row r = cache.get(id);
        if (r == null) {
            r = new Row();
            cache.put(id, r);
        }
        return r;
    }

    /** Called whenever a track becomes the one actually playing. */
    public static synchronized void started(Context c, long id) {
        if (id < 0 || !Store.aiEnabled(c)) return;
        load(c);
        Row r = row(id);
        r.lastPlayed = System.currentTimeMillis() / 1000;
        hours[Calendar.getInstance().get(Calendar.HOUR_OF_DAY)]++;
        save(c);
    }

    /** Called right as a track stops being current, saying whether it played through or was skipped away from. */
    public static synchronized void ended(Context c, long id, boolean finishedNaturally) {
        if (id < 0 || !Store.aiEnabled(c)) return;
        load(c);
        Row r = row(id);
        if (finishedNaturally) r.plays++;
        else r.skips++;
        save(c);
    }

    public static synchronized Map<Long, Row> all(Context c) {
        load(c);
        return cache;
    }

    public static synchronized int[] hourHistogram(Context c) {
        load(c);
        return hours;
    }

    /** Wipes every bit of stored listening history. */
    public static synchronized void clear(Context c) {
        cache = new HashMap<>();
        hours = new int[24];
        prefs(c).edit().clear().apply();
    }
}
