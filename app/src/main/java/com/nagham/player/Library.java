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
    private static final List<Runnable> WAITERS = new ArrayList<>();
    private static boolean scanning, again;
    private static List<Track> sortedCache;
    private static int sortedMode = -1;

    /** One scan at a time: a request that arrives mid-scan just re-runs it once more afterwards. */
    public static void scan(Context ctx, final Runnable done) {
        final Context c = ctx.getApplicationContext();
        synchronized (Library.class) {
            if (done != null) WAITERS.add(done);
            if (scanning) {
                again = true;
                return;
            }
            scanning = true;
        }
        EX.execute(() -> {
            boolean rerun;
            do {
                synchronized (Library.class) {
                    again = false;
                }
                read(c);
                synchronized (Library.class) {
                    rerun = again;
                }
            } while (rerun);
            List<Runnable> w;
            synchronized (Library.class) {
                scanning = false;
                w = new ArrayList<>(WAITERS);
                WAITERS.clear();
            }
            for (Runnable r : w) MAIN.post(r);
        });
    }

    private static void read(Context c) {
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
                    String title = Fmt.title(q.getString(1), dot > 0 ? name.substring(0, dot) : name);
                    String artist = q.getString(2);
                    if (artist == null || "<unknown>".equals(artist)) artist = "";
                    else artist = Fmt.fix(artist);
                    String album = q.getString(3) == null ? "" : Fmt.fix(q.getString(3));
                    out.add(new Track(id, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            title, artist, album, ext, dur, q.getLong(5)));
                }
            } catch (Exception ignored) {
            }
        }
        synchronized (Library.class) {
            tracks = out;
            sortedCache = null;
            sortedMode = -1;
        }
        byId.clear();
        for (Track t : out) byId.put(t.id, t);
        loaded = true;
        sorted(Store.sort(c)); // pre-warm on this background thread so the first screen draw is instant
    }

    /** Cached sorted view of the library (do not modify). Modes: 0 title, 1 artist, 2 recently added, 3 duration. */
    public static synchronized List<Track> sorted(int mode) {
        if (sortedCache != null && sortedMode == mode) return sortedCache;
        List<Track> l = new ArrayList<>(tracks);
        sort(l, mode);
        sortedCache = l;
        sortedMode = mode;
        return l;
    }

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

    public static int count(List<Long> ids) {
        int n = 0;
        for (long id : ids) if (byId.containsKey(id)) n++;
        return n;
    }
}
