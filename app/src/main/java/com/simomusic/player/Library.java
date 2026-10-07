package com.simomusic.player;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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

    /** Screens that show the library: told (on the main thread) whenever the device's songs really changed. */
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile long lastGen = -1, lastScanAt;

    public static void addListener(Runnable r) {
        if (r != null && !LISTENERS.contains(r)) LISTENERS.add(r);
    }

    public static void removeListener(Runnable r) {
        LISTENERS.remove(r);
    }

    private static void fire() {
        MAIN.post(() -> {
            for (Runnable r : LISTENERS) {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    CrashGuard.nonFatal("library listener", e);
                }
            }
        });
    }

    /**
     * Cheap "did the phone's music change while we were away?" check, used when a screen comes back to the front.
     * Android 11+ keeps a generation counter for the media database, so nothing is re-read when nothing changed.
     */
    public static void syncIfChanged(Context c) {
        if (!Perms.hasAudio(c)) return;
        if (Build.VERSION.SDK_INT >= 30) {
            long g = -1;
            try {
                g = MediaStore.getGeneration(c, MediaStore.VOLUME_EXTERNAL);
            } catch (RuntimeException ignored) {
            }
            if (loaded && g != -1 && g == lastGen) return;
        } else if (loaded && SystemClock.elapsedRealtime() - lastScanAt < 3000) {
            return;
        }
        scan(c, null);
    }

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
        long gen = -1;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                gen = MediaStore.getGeneration(c, MediaStore.VOLUME_EXTERNAL);
            } catch (RuntimeException ignored) {
            }
        }
        List<Track> out = new ArrayList<>();
        boolean ok = true;
        boolean access = Perms.hasAudio(c);
        if (access) {
            Set<String> allowed = Store.enabledExts(c);
            long minMs = Store.minDur(c) * 1000L;
            String[] proj = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DISPLAY_NAME};
            String sel = MediaStore.Audio.Media.IS_RINGTONE + "=0 AND " + MediaStore.Audio.Media.IS_NOTIFICATION
                    + "=0 AND " + MediaStore.Audio.Media.IS_ALARM + "=0";
            try (Cursor q = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel, null, null)) {
                if (q == null) {
                    ok = false;
                } else {
                    while (q.moveToNext()) {
                        String name = q.getString(6) == null ? "" : q.getString(6);
                        int dot = name.lastIndexOf('.');
                        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
                        long dur = q.getLong(4);
                        if (!allowed.contains(ext) || dur <= 0 || dur < minMs) continue;
                        long id = q.getLong(0);
                        String title = Fmt.title(q.getString(1), dot > 0 ? name.substring(0, dot) : name);
                        String artist = q.getString(2);
                        if (artist == null || "<unknown>".equals(artist)) artist = "";
                        else artist = Fmt.fix(artist);
                        String album = q.getString(3) == null ? "" : Fmt.fix(q.getString(3));
                        out.add(new Track(id, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                                title, artist, album, ext, dur, q.getLong(5)));
                    }
                }
            } catch (Exception e) {
                // a failed read must never wipe the list the user is looking at: keep the previous one
                CrashGuard.nonFatal("library scan", e);
                ok = false;
            }
        }
        lastScanAt = SystemClock.elapsedRealtime();
        if (!ok && loaded) return;

        List<Track> before = tracks;
        boolean changed = !loaded || !sameList(before, out);
        Set<Long> gone = new HashSet<>();
        if (changed) {
            Set<Long> now = new HashSet<>();
            for (Track t : out) now.add(t.id);
            for (Track t : before) if (!now.contains(t.id)) gone.add(t.id);
            synchronized (Library.class) {
                tracks = out;
                sortedCache = null;
                sortedMode = -1;
            }
            byId.clear();
            for (Track t : out) byId.put(t.id, t);
        }
        lastGen = gen;
        loaded = true;
        if (changed) {
            sorted(Store.sort(c)); // pre-warm on this background thread so the first screen draw is instant
            if (access && ok && !gone.isEmpty()) dropDeletedFromQueue(c, gone);
            fire();
        }
    }

    private static boolean sameList(List<Track> a, List<Track> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Track x = a.get(i), y = b.get(i);
            if (x.id != y.id || x.duration != y.duration || !x.title.equals(y.title) || !x.artist.equals(y.artist)
                    || !x.album.equals(y.album)) return false;
        }
        return true;
    }

    /**
     * Songs that vanished from the list are taken out of the play queue only when the file is really gone from the
     * phone (a hidden format or a short song that is merely filtered out stays queued).
     */
    private static void dropDeletedFromQueue(Context c, Set<Long> gone) {
        final Set<Long> dead = new HashSet<>();
        int checked = 0;
        for (long id : gone) {
            if (++checked > 300) break;
            try (Cursor q = c.getContentResolver().query(
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    new String[]{MediaStore.Audio.Media._ID}, null, null, null)) {
                if (q != null && q.getCount() == 0) dead.add(id);
            } catch (Exception ignored) {
            }
        }
        if (dead.isEmpty()) return;
        MAIN.post(() -> {
            if (Pb.get() != null) Pb.removeIds(dead);
        });
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

    /** Takes songs out of the in-memory library right after they were deleted from the phone. */
    public static synchronized void remove(java.util.Collection<Long> gone) {
        List<Track> n = new ArrayList<>();
        for (Track t : tracks) if (!gone.contains(t.id)) n.add(t);
        tracks = n;
        sortedCache = null;
        sortedMode = -1;
        for (Long id : gone) byId.remove(id);
        fire();
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
