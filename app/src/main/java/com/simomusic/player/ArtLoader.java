package com.simomusic.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.OptIn;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Gives the media notification and the system lock screen a cover bitmap for each track. The track's own
 * content:// URI is used as the "artwork URI", so the art is read from the file itself (works on Android 10-16
 * without storage-wide permissions).
 */
@OptIn(markerClass = UnstableApi.class)
final class ArtLoader implements BitmapLoader {
    private final Context ctx;

    ArtLoader(Context c) {
        ctx = c.getApplicationContext();
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return true;
    }

    @Override
    public ListenableFuture<Bitmap> decodeBitmap(byte[] data) {
        SettableFuture<Bitmap> f = SettableFuture.create();
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (b != null) f.set(b);
        else f.setException(new IllegalArgumentException("bad image"));
        return f;
    }

    @Override
    public ListenableFuture<Bitmap> loadBitmap(final Uri uri) {
        final SettableFuture<Bitmap> f = SettableFuture.create();
        Art.EX.execute(() -> {
            Bitmap b = Art.loadSync(ctx, uri, 512);
            if (b != null) f.set(b);
            else f.setException(new IllegalStateException("no cover"));
        });
        return f;
    }
}
