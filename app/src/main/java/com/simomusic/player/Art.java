package com.simomusic.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import androidx.core.widget.ImageViewCompat;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Cover-art loading (embedded art / MediaStore thumbnails) with a small memory cache. */
public final class Art {
    private Art() {
    }

    public interface Cb {
        void got(Bitmap b);
    }

    /**
     * Newest request first: while a list is being flung the covers of the rows that are on screen NOW are loaded
     * before the ones that scrolled past (a plain FIFO queue made every cover wait for all the old rows), and the
     * threads run below the UI thread's priority so decoding never steals frames from scrolling.
     */
    private static final class Lifo extends LinkedBlockingDeque<Runnable> {
        @Override
        public boolean offer(Runnable r) {
            return offerFirst(r);
        }
    }

    public static final ExecutorService EX = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, new Lifo(), new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
            Thread t = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }, "cover-art");
            t.setDaemon(true);
            return t;
        }
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<String> MISSING = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>((int) Math.min(48L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8)) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };

    /** Memory pressure: drop half of the cached covers, or all of them. */
    public static void trim(boolean all) {
        if (all) {
            CACHE.evictAll();
            MISSING.clear();
        } else {
            CACHE.trimToSize(CACHE.maxSize() / 2);
        }
    }

    /** Blocking: call from a background thread. */
    public static Bitmap loadSync(Context c, Uri uri, int px) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return c.getContentResolver().loadThumbnail(uri, new Size(px, px), null);
            } catch (Exception ignored) {
            }
        }
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, uri);
            byte[] b = r.getEmbeddedPicture();
            if (b == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(b, 0, b.length, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            return BitmapFactory.decodeByteArray(b, 0, b.length, o);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    /** Async, cached; the callback runs on the main thread with null when the file has no art. */
    public static void fetch(Context c, final Uri uri, final int px, final Cb cb) {
        fetch(c, uri, px, cb, null);
    }

    /** `wanted` is asked again just before the work starts: a row that was scrolled away meanwhile costs nothing. */
    public static void fetch(Context c, final Uri uri, final int px, final Cb cb, final BooleanSupplier wanted) {
        final Context app = c.getApplicationContext();
        final String key = uri + "@" + px;
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            cb.got(hit);
            return;
        }
        if (MISSING.contains(key)) {
            cb.got(null);
            return;
        }
        EX.execute(() -> {
            if (wanted != null && !wanted.getAsBoolean()) return;
            Bitmap rr = null;
            try {
                rr = loadSync(app, uri, px);
            } catch (Throwable t) {          // out of memory, corrupt tags, vanished file: show the placeholder instead of crashing
                CrashGuard.nonFatal("cover art", t);
            }
            final Bitmap r = rr;
            if (r != null) CACHE.put(key, r);
            else MISSING.add(key);
            MAIN.post(() -> cb.got(r));
        });
    }

    /** Loads art into an ImageView, guarding against recycled rows. */
    public static void load(Context c, final Uri uri, final ImageView iv, final int px) {
        final String key = String.valueOf(uri);
        if (key.equals(iv.getTag(R.id.tag_art)) && iv.getTag(R.id.tag_art_ok) != null) return;
        iv.setTag(R.id.tag_art, key);
        iv.setTag(R.id.tag_art_ok, null);
        if (uri != null) {
            Bitmap cached = CACHE.get(key + "@" + px);
            if (cached != null) {
                show(iv, cached, px);
                iv.setTag(R.id.tag_art_ok, Boolean.TRUE);
                return;
            }
        }
        show(iv, null, px);
        if (uri == null) return;
        fetch(c, uri, px, b -> {
            if (b != null && key.equals(iv.getTag(R.id.tag_art))) {
                show(iv, b, px);
                iv.setTag(R.id.tag_art_ok, Boolean.TRUE);
            }
        }, () -> key.equals(iv.getTag(R.id.tag_art)));
    }

    private static final int[][] GRAD = {
            {0xFF4A3590, 0xFF16112E}, {0xFF1F5E85, 0xFF0B1C2C}, {0xFF8A3550, 0xFF2B1019}, {0xFF2F7A57, 0xFF0F2B1F},
            {0xFF8A702F, 0xFF2B2210}, {0xFF2F4E8A, 0xFF0F172B}, {0xFF6E358A, 0xFF1F102B}, {0xFF2F8A82, 0xFF0F2B29}};

    /** Every song without cover gets its own deterministic gradient, so a list of "no cover" rows still looks designed. */
    private static void tintBg(ImageView iv) {
        android.graphics.drawable.Drawable d = iv.getBackground();
        if (!(d instanceof android.graphics.drawable.GradientDrawable)) return;
        Object k = iv.getTag(R.id.tag_art);
        int[] g = GRAD[((k == null ? 0 : k.hashCode()) & 0x7fffffff) % GRAD.length];
        android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) d.mutate();
        gd.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
        gd.setColors(g);
    }

    public static void show(ImageView iv, Bitmap b, int px) {
        if (b == null) {
            tintBg(iv);
            iv.setImageResource(R.drawable.ic_music);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = px / 4;
            iv.setPadding(p, p, p, p);
            androidx.core.widget.ImageViewCompat.setImageTintList(iv, android.content.res.ColorStateList.valueOf(0x99FFFFFF));
        } else {
            ImageViewCompat.setImageTintList(iv, null);
            iv.setPadding(0, 0, 0, 0);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(b);
        }
    }
}
