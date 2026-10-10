package com.simomusic.player;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;

/**
 * Gives memory back when Android asks for it: cover bitmaps are the biggest thing the app caches, so they are the
 * first to go (half of them when the screens are hidden, all of them when the system is really short of memory).
 * Playback itself is not affected, and covers simply reload when they are needed again.
 */
public final class MemoryGuard implements ComponentCallbacks2 {
    private MemoryGuard() {
    }

    public static void install(Application a) {
        a.registerComponentCallbacks(new MemoryGuard());
    }

    @Override
    public void onTrimMemory(int level) {
        if (level >= TRIM_MEMORY_BACKGROUND || level == TRIM_MEMORY_RUNNING_CRITICAL) Art.trim(true);
        else if (level >= TRIM_MEMORY_UI_HIDDEN) Art.trim(false);
    }

    @Override
    public void onLowMemory() {
        Art.trim(true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }
}
