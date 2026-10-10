package com.simomusic.player;

import android.os.Parcelable;
import android.util.SparseArray;

import androidx.recyclerview.widget.RecyclerView;

/** Remembers where each tab's list was scrolled to, so switching Songs / Playlists and back keeps your place. */
public final class ListState {
    private final SparseArray<Parcelable> saved = new SparseArray<>();

    public void save(int key, RecyclerView rv) {
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (lm != null) saved.put(key, lm.onSaveInstanceState());
    }

    public void restore(int key, RecyclerView rv) {
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        Parcelable p = saved.get(key);
        if (lm != null && p != null) lm.onRestoreInstanceState(p);
    }
}
