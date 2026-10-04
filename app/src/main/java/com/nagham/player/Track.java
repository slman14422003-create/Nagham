package com.nagham.player;

import android.net.Uri;

/** One audio file from MediaStore. */
public final class Track {
    public final long id;
    public final Uri uri;
    public final String title, artist, album, ext, key;
    public final long duration, added;

    public Track(long id, Uri uri, String title, String artist, String album, String ext, long duration, long added) {
        this.id = id;
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.ext = ext;
        this.duration = duration;
        this.added = added;
        this.key = (title + " " + artist + " " + album).toLowerCase();
    }
}
