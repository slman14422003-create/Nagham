package com.nagham.player;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One playlist: play / shuffle, drag handle to reorder, swipe a row away to remove, + to add many songs at once. */
public class PlaylistActivity extends AppCompatActivity implements TrackAdapter.Listener {
    private String pid;
    private TrackAdapter ad;
    private LinearLayout root;
    private TextView empty;
    private LinearLayout titleHolder;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pid = getIntent().getStringExtra("pid");
        if (pid == null || Store.playlist(this, pid) == null) {
            finish();
            return;
        }
        Pb.connect(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        titleHolder = new LinearLayout(this);
        root.addView(titleHolder);

        root.addView(Ui.pillRow(this,
                Ui.pill(this, R.string.play_all, R.drawable.ic_play, true, v -> play(false)),
                Ui.pill(this, R.string.shuffle_all, R.drawable.ic_shuffle, false, v -> play(true))));

        FrameLayout body = new FrameLayout(this);
        ad = new TrackAdapter(this, this);
        ad.reorder = true;
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(ad);
        rv.setClipToPadding(false);
        rv.setPadding(0, 0, 0, Ui.dp(this, 16));
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int a = from.getBindingAdapterPosition(), c = to.getBindingAdapterPosition();
                if (a < 0 || c < 0 || a >= ad.data.size() || c >= ad.data.size()) return false;
                ad.data.add(c, ad.data.remove(a));   // a fast drag can skip rows: move, never swap
                ad.notifyItemMoved(a, c);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int p = vh.getBindingAdapterPosition();
                if (p < 0 || p >= ad.data.size()) return;
                ad.data.remove(p);
                ad.notifyItemRemoved(p);
                saveOrder();
                updateEmpty();
                final RecyclerView rr = (RecyclerView) vh.itemView.getParent();
                if (rr != null) rr.post(() -> {
                    int n = ad.data.size();
                    if (n > 0) ad.notifyItemRangeChanged(0, n);
                });
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void clearView(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(r, vh);
                saveOrder();
                r.post(() -> {
                    int n = ad.data.size();
                    if (n > 0) ad.notifyItemRangeChanged(0, n);   // refreshes first / last corner shapes
                });
            }
        });
        helper.attachToRecyclerView(rv);
        ad.helper = helper;
        body.addView(rv, new FrameLayout.LayoutParams(-1, -1));
        empty = Ui.text(this, getString(R.string.empty_playlist), 15, R.color.text_secondary);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 36), 0, Ui.dp(this, 36), 0);
        body.addView(empty, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store.Playlist p = Store.playlist(this, pid);
        if (p == null) {
            finish();
            return;
        }
        titleHolder.removeAllViews();
        // rebuilt on every resume so a rename shows immediately
        List<ImageButton> acts = new ArrayList<>();
        acts.add(Ui.icon(this, R.drawable.ic_add, R.string.add_songs, v -> {
            Intent it = new Intent(this, PickSongsActivity.class);
            it.putExtra("pid", pid);
            startActivity(it);
        }));
        if (!Store.FAV.equals(pid)) acts.add(Ui.icon(this, R.drawable.ic_more, R.string.more, v -> manage()));
        titleHolder.addView(Ui.topBar(this, p.name, R.drawable.ic_back, acts.toArray(new ImageButton[0])),
                new LinearLayout.LayoutParams(-1, -2));
        ad.setData(Library.resolve(p.ids));
        updateEmpty();
    }

    private void updateEmpty() {
        empty.setVisibility(ad.data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void saveOrder() {
        List<Long> ids = new ArrayList<>();
        for (Track t : ad.data) ids.add(t.id);
        Store.setOrder(this, pid, ids);
    }

    private void play(boolean shuffle) {
        if (ad.data.isEmpty()) return;
        Pb.play(this, new ArrayList<>(ad.data), shuffle ? -1 : 0, shuffle);
    }

    private void manage() {
        final Store.Playlist p = Store.playlist(this, pid);
        if (p == null) return;
        Sheet.show(this, p.name, () -> {
            List<Sheet.Item> l = new ArrayList<>();
            l.add(Sheet.item(R.drawable.ic_edit, getString(R.string.rename), false, false, () ->
                    Menus.ask(this, R.string.rename, p.name, name -> {
                        Store.rename(this, pid, name);
                        onResume();
                    })));
            l.add(Sheet.item(R.drawable.ic_delete, getString(R.string.delete), false, false, () ->
                    Dlg.confirm(this, getString(R.string.delete),
                            getString(R.string.confirm_delete_playlist, p.name), R.string.delete, true, () -> {
                                Store.delete(this, pid);
                                finish();
                            })));
            return l;
        });
    }

    @Override
    public void onClick(Track t, int pos) {
        Pb.play(this, new ArrayList<>(ad.data), pos, false);
    }

    @Override
    public void onMore(Track t, int pos) {
    }
}
