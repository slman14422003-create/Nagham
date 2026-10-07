package com.simomusic.player;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;

import androidx.core.content.IntentCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Entry point for "open with" / share / voice search: audio files from file managers, WhatsApp, browsers (https links),
 * "play X on SimoMusic". No UI: it starts playback and shows the player.
 */
public class OpenActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            handle(getIntent());
        } catch (Throwable t) {
            CrashGuard.nonFatal("open file", t);
            Ui.toast(this, R.string.open_failed);
            finish();
        }
    }

    private void handle(Intent in) {
        String a = in.getAction();
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(a)) {
            playSearch(in.getStringExtra(SearchManager.QUERY));
            return;
        }
        Uri u = in.getData();
        if (u == null && Intent.ACTION_SEND.equals(a)) u = IntentCompat.getParcelableExtra(in, Intent.EXTRA_STREAM, Uri.class);
        if (u == null) {
            Ui.go(this, new Intent(this, MainActivity.class));
            finish();
            return;
        }
        open(u);
    }

    private void showPlayer() {
        Ui.go(this, new Intent(this, PlayerActivity.class));
        finish();
    }

    private void open(final Uri u) {
        // a song that is already in the library: play it inside the whole library queue
        if ("content".equals(u.getScheme()) && "media".equals(u.getAuthority()) && Perms.hasAudio(this)) {
            long id = -1;
            try {
                id = ContentUris.parseId(u);
            } catch (Exception ignored) {
            }
            if (id >= 0) {
                final long fid = id;
                withLibrary(() -> {
                    Track t = Library.byId.get(fid);
                    if (t != null) {
                        List<Track> all = Library.sorted(Store.sort(this));
                        Pb.play(this, new ArrayList<>(all), Math.max(0, all.indexOf(t)), false);
                        showPlayer();
                    } else {
                        stream(u);
                    }
                });
                return;
            }
        }
        if ("https".equals(u.getScheme()) || "http".equals(u.getScheme())) {
            Pb.playItem(this, new MediaItem.Builder().setMediaId("url:" + u).setUri(u)
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(name(u)).build()).build());
            showPlayer();
            return;
        }
        stream(u);
    }

    /** Other apps' files: copied into the cache first, because their temporary read grant ends with this screen. */
    private void stream(final Uri u) {
        new Thread(() -> {
            try {
                final File f = copy(u);
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                String title = null, artist = null, album = null;
                try {
                    r.setDataSource(f.getAbsolutePath());
                    title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                    artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                } catch (Exception ignored) {
                } finally {
                    try {
                        r.release();
                    } catch (Exception ignored) {
                    }
                }
                final MediaItem item = new MediaItem.Builder().setMediaId("file:" + f.getName()).setUri(Uri.fromFile(f))
                        .setMediaMetadata(new MediaMetadata.Builder()
                                .setTitle(title == null || title.isEmpty() ? strip(f.getName()) : title)
                                .setArtist(artist).setAlbumTitle(album)
                                .setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build())
                        .build();
                runOnUiThread(() -> {
                    Pb.playItem(this, item);
                    showPlayer();
                });
            } catch (Throwable t) {
                CrashGuard.nonFatal("open file", t);
                runOnUiThread(() -> {
                    Ui.toast(this, R.string.open_failed);
                    finish();
                });
            }
        }).start();
    }

    private File copy(Uri u) throws Exception {
        File dir = new File(getCacheDir(), "open");
        dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null && old.length > 4) {           // keep only the newest few
            java.util.Arrays.sort(old, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
            for (int i = 0; i < old.length - 4; i++) old[i].delete();
        }
        String n = name(u).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (!n.contains(".")) n += ".mp3";
        File f = new File(dir, System.currentTimeMillis() % 100000 + "_" + n);
        try (InputStream in = "file".equals(u.getScheme()) ? new java.io.FileInputStream(u.getPath()) : getContentResolver().openInputStream(u);
             OutputStream out = new FileOutputStream(f)) {
            if (in == null) throw new java.io.FileNotFoundException(String.valueOf(u));
            byte[] buf = new byte[64 * 1024];
            int k;
            while ((k = in.read(buf)) > 0) out.write(buf, 0, k);
        }
        return f;
    }

    private String name(Uri u) {
        String n = null;
        if ("content".equals(u.getScheme())) {
            try (Cursor q = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (q != null && q.moveToFirst() && !q.isNull(0)) n = q.getString(0);
            } catch (Exception ignored) {
            }
        }
        if (n == null) n = u.getLastPathSegment();
        return n == null || n.isEmpty() ? "audio" : n;
    }

    private static String strip(String n) {
        int i = n.lastIndexOf('.');
        String s = i > 0 ? n.substring(0, i) : n;
        return s.replaceFirst("^\\d{1,5}_", "");
    }

    private void playSearch(final String q) {
        withLibrary(() -> {
            List<Track> all = Library.sorted(Store.sort(this)), hit = new ArrayList<>();
            String k = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty()) {
                for (Track t : all) {
                    if (t.title.toLowerCase(Locale.ROOT).contains(k) || (t.artist != null && t.artist.toLowerCase(Locale.ROOT).contains(k))
                            || (t.album != null && t.album.toLowerCase(Locale.ROOT).contains(k))) hit.add(t);
                }
            }
            boolean shuffle = hit.isEmpty();
            List<Track> play = hit.isEmpty() ? all : hit;
            if (play.isEmpty()) {
                Ui.go(this, new Intent(this, MainActivity.class));
                finish();
                return;
            }
            Pb.play(this, new ArrayList<>(play), 0, shuffle);
            showPlayer();
        });
    }

    private void withLibrary(final Runnable r) {
        if (Library.loaded) {
            r.run();
        } else if (Perms.hasAudio(this)) {
            Library.scan(this, r);
        } else {
            Ui.go(this, new Intent(this, MainActivity.class));
            finish();
        }
    }
}
