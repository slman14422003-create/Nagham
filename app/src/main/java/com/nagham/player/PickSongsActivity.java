package com.nagham.player;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Multi-select song picker with search: tick as many songs as you like, one tap adds them all to the playlist. */
public class PickSongsActivity extends AppCompatActivity implements TrackAdapter.Listener {
    private String pid;
    private TrackAdapter ad;
    private Button add;
    private final List<Track> pool = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pid = getIntent().getStringExtra("pid");
        Store.Playlist p = pid == null ? null : Store.playlist(this, pid);
        if (p == null) {
            finish();
            return;
        }
        for (Track t : Library.tracks) if (!p.ids.contains(t.id)) pool.add(t);
        Library.sort(pool, Store.sort(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        android.widget.ImageButton all = Ui.icon(this, R.drawable.ic_check, R.string.select_all, v -> {
            for (Track t : ad.data) ad.selected.add(t.id);
            ad.notifyDataSetChanged();
            count();
        });
        root.addView(Ui.topBar(this, getString(R.string.add_songs), R.drawable.ic_back, all));

        android.widget.EditText search = Ui.searchEdit(this, getString(R.string.search_hint));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }

            @Override
            public void afterTextChanged(Editable e) {
                String q = e.toString().trim().toLowerCase(Locale.getDefault());
                List<Track> f = new ArrayList<>();
                for (Track t : pool) {
                    if (q.isEmpty() || t.title.toLowerCase().contains(q) || (t.artist != null && t.artist.toLowerCase().contains(q))) f.add(t);
                }
                ad.setData(f);
            }
        });
        root.addView(search);

        ad = new TrackAdapter(this, this);
        ad.select = true;
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(ad);
        rv.setClipToPadding(false);
        rv.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        root.addView(rv, new LinearLayout.LayoutParams(-1, 0, 1f));
        ad.setData(pool);

        add = Ui.pill(this, R.string.add_selected, R.drawable.ic_check, true, v -> {
            int n = Store.addAll(this, pid, new ArrayList<>(ad.selected));
            Toast.makeText(this, getString(R.string.done_n, n), Toast.LENGTH_SHORT).show();
            finish();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), Ui.dp(this, 14));
        root.addView(add, lp);
        count();
        setContentView(root);
    }

    private void count() {
        int n = ad.selected.size();
        add.setEnabled(n > 0);
        add.setAlpha(n > 0 ? 1f : 0.45f);
        add.setText(n > 0 ? getString(R.string.add_n, n) : getString(R.string.add_selected));
    }

    @Override
    public void onClick(Track t, int pos) {
        count();
    }

    @Override
    public void onMore(Track t, int pos) {
    }
}
