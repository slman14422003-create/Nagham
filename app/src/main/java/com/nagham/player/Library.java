package com.nagham.player;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads the device's audio files from MediaStore, filtered by the formats enabled in Settings. */
public final class Library {
    private Library() {
    }

    public static volatile List<Track> tracks = new ArrayList<>();
    public static final Map<Long, Track> byId = new ConcurrentHashMap<>();
    public static volatile boolean loaded;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();

    public static void scan(Context ctx, final Runnable done) {
        final Context c = ctx.getApplicationContext();
        EX.execute(() -> {
            List<Track> out = new ArrayList<>();
            if (Perms.hasAudio(c)) {
                Set<String> ok = Store.enabledExts(c);
                long minMs = Store.minDur(c) * 1000L;
                String[] proj = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED,
                        MediaStore.Audio.Media.DISPLAY_NAME};
                String sel = MediaStore.Audio.Media.IS_RINGTONE + "=0 AND " + MediaStore.Audio.Media.IS_NOTIFICATION
                        + "=0 AND " + MediaStore.Audio.Media.IS_ALARM + "=0";
                try (Cursor q = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel, null, null)) {
                    while (q != null && q.moveToNext()) {
                        String name = q.getString(6) == null ? "" : q.getString(6);
                        int dot = name.lastIndexOf('.');
                        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
                        long dur = q.getLong(4);
                        if (!ok.contains(ext) || dur <= 0 || dur < minMs) continue;
                        long id = q.getLong(0);
                        String title = q.getString(1);
                        if (title == null || title.isEmpty()) title = dot > 0 ? name.substring(0, dot) : name;
                        String artist = q.getString(2);
                        if (artist == null || "<unknown>".equals(artist)) artist = "";
                        out.add(new Track(id, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                                title, artist, q.getString(3), ext, dur, q.getLong(5)));
                    }
                } catch (Exception ignored) {
                }
            }
            tracks = out;
            byId.clear();
            for (Track t : out) byId.put(t.id, t);
            loaded = true;
            if (done != null) MAIN.post(done);
        });
    }

    /** Sorts in place: 0 title, 1 artist, 2 recently added, 3 duration. */
    public static void sort(List<Track> l, int mode) {
        final Collator col = Collator.getInstance();
        Comparator<Track> cmp;
        switch (mode) {
            case 1:
                cmp = (a, b) -> col.compare(a.artist, b.artist);
                break;
            case 2:
                cmp = (a, b) -> Long.compare(b.added, a.added);
                break;
            case 3:
                cmp = (a, b) -> Long.compare(b.duration, a.duration);
                break;
            default:
                cmp = (a, b) -> col.compare(a.title, b.title);
        }
        Collections.sort(l, cmp);
    }

    public static List<Track> resolve(List<Long> ids) {
        List<Track> out = new ArrayList<>();
        for (long id : ids) {
            Track t = byId.get(id);
            if (t != null) out.add(t);
        }
        return out;
    }
}
