package com.simomusic.player;

import android.content.Context;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

/**
 * Loads the covers of the next two songs in the queue a moment before they play, so a skip (or the end of a song)
 * shows the new cover at once instead of an empty square that fills in later.
 */
public final class Prefetch {
    private Prefetch() {
    }

    private static String lastKey = "";

    /** @param sizes the cover sizes (in pixels) the calling screen draws */
    public static void upcoming(Context c, Player m, int... sizes) {
        if (m == null || sizes == null || sizes.length == 0) return;
        try {
            MediaItem cur = m.getCurrentMediaItem();
            String key = (cur == null ? "" : cur.mediaId) + "#" + sizes[0];
            if (key.equals(lastKey)) return;
            lastKey = key;
            int n = m.getMediaItemCount();
            int idx = m.getCurrentMediaItemIndex();
            for (int step = 1; step <= 2; step++) {
                int i = step == 1 ? m.getNextMediaItemIndex() : idx + 2;
                if (i < 0 || i >= n || i == idx) continue;
                for (int px : sizes) {
                    if (px > 0) Art.fetch(c, m.getMediaItemAt(i).mediaMetadata.artworkUri, px, b -> {
                    });
                }
            }
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("prefetch", e);
        }
    }
}
