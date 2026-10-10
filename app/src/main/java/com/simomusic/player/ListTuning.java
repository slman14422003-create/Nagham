package com.simomusic.player;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

/**
 * One place for the scrolling settings every song list shares: rows are costly to build, so more of them are kept
 * ready, a changed row is redrawn in place instead of cross-fading (no flicker when the playing song changes),
 * and the list never resizes itself because its rows change.
 */
public final class ListTuning {
    private ListTuning() {
    }

    public static void apply(RecyclerView rv) {
        rv.setHasFixedSize(true);
        rv.setItemViewCacheSize(12);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.getRecycledViewPool().setMaxRecycledViews(0, 24);
        RecyclerView.ItemAnimator ia = rv.getItemAnimator();
        if (ia instanceof SimpleItemAnimator) ((SimpleItemAnimator) ia).setSupportsChangeAnimations(false);
    }
}
