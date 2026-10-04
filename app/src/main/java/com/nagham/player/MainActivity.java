package com.nagham.player;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Home: all songs (search / sort / play all / shuffle), playlists, banner for missing permissions, mini player. */
public class MainActivity extends AppCompatActivity implements TrackAdapter.Listener {
    private TrackAdapter songs;
    private RecyclerView rv;
    private TextView chipSongs, chipLists, empty;
    private LinearLayout actions;
    private android.widget.EditText search;
    private View banner;
    private MiniPlayer mini;
    private int tab = 0;
    private String query = "";
    private List<Track> shown = new ArrayList<>();
    private final PlaylistAdapter lists = new PlaylistAdapter();

    private final ActivityResultLauncher<String[]> askPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> rescan());

    private final Player.Listener current = new Player.Listener() {
        @Override
        public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) {
            songs.setCurrent(Pb.currentId());
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(b);
        Pb.connect(this);
        build();
        if (!Perms.hasAudio(this) && !Store.flag(this, "first_done", false)) {
            Store.setFlag(this, "first_done", true);
            askPerms.launch(Perms.firstRun());
        } else {
            rescan();
        }
    }

    @SuppressLint("SetTextI18n")
    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPaddingRelative(Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 6));
        TextView title = Ui.text(this, getString(R.string.app_name), 32, R.color.text_primary);
        title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        bar.addView(title, Ui.weight(1));
        bar.addView(Ui.icon(this, R.drawable.ic_search, R.string.search, v -> toggleSearch()));
        bar.addView(Ui.icon(this, R.drawable.ic_sort, R.string.sort_by, v -> Menus.sort(this, this::refresh)));
        bar.addView(Ui.icon(this, R.drawable.ic_settings, R.string.settings,
                v -> startActivity(new Intent(this, SettingsActivity.class))));
        for (int i = 1; i < bar.getChildCount(); i++) ((LinearLayout.LayoutParams) bar.getChildAt(i).getLayoutParams()).setMarginStart(Ui.dp(this, 8));
        root.addView(bar);

        search = Ui.edit(this, getString(R.string.search_hint), null);
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable e) {
                query = e.toString().trim().toLowerCase(Locale.getDefault());
                refresh();
            }
        });
        root.addView(search);

        banner = Ui.settingRow(this, R.drawable.ic_shield, getString(R.string.finish_setup), getString(R.string.finish_setup_sub),
                null, false, v -> startActivity(new Intent(this, SettingsActivity.class)));
        banner.setVisibility(View.GONE);
        Ui.shape(this, banner.findViewById(R.id.card), true, true, R.color.accent_soft);
        root.addView(banner);

        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 4));
        chipSongs = Ui.chip(this, "", true);
        chipLists = Ui.chip(this, "", false);
        chipSongs.setOnClickListener(v -> setTab(0));
        chipLists.setOnClickListener(v -> setTab(1));
        chips.addView(chipSongs);
        chips.addView(chipLists);
        root.addView(chips);

        actions = new LinearLayout(this);
        actions.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), Ui.dp(this, 8));
        root.addView(actions);

        FrameLayout body = new FrameLayout(this);
        songs = new TrackAdapter(this, this);
        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setClipToPadding(false);
        rv.setPadding(0, 0, 0, Ui.dp(this, 8));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        body.addView(rv, new FrameLayout.LayoutParams(-1, -1));
        empty = Ui.text(this, "", 15, R.color.text_secondary);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 36), 0, Ui.dp(this, 36), 0);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        body.addView(empty, ep);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        mini = new MiniPlayer(this);
        root.addView(mini);
        setContentView(root);
        setTab(0);
    }

    private void toggleSearch() {
        boolean show = search.getVisibility() != View.VISIBLE;
        search.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            setTab(0);
            search.requestFocus();
        } else {
            search.setText("");
        }
    }

    private void setTab(int t) {
        tab = t;
        Ui.setChip(this, chipSongs, t == 0);
        Ui.setChip(this, chipLists, t == 1);
        actions.removeAllViews();
        if (t == 0) {
            actions.addView(Ui.pill(this, R.string.play_all, R.drawable.ic_play, true, v -> playAll(false)));
            View sp = new View(this);
            actions.addView(sp, Ui.lp(Ui.dp(this, 10), 1));
            actions.addView(Ui.pill(this, R.string.shuffle_all, R.drawable.ic_shuffle, false, v -> playAll(true)));
        } else {
            actions.addView(Ui.pill(this, R.string.new_playlist, R.drawable.ic_add, true, v ->
                    Menus.ask(this, R.string.new_playlist, "", name -> {
                        Store.create(this, name);
                        refresh();
                    })));
        }
        refresh();
    }

    private void playAll(boolean shuffle) {
        if (shown.isEmpty()) return;
        Pb.play(this, shown, shuffle ? -1 : 0, shuffle);
    }

    private void rescan() {
        Library.scan(this, this::refresh);
        refresh();
    }

    private void refresh() {
        List<Track> all = new ArrayList<>(Library.tracks);
        if (!query.isEmpty()) {
            List<Track> f = new ArrayList<>();
            for (Track t : all) {
                if (t.title.toLowerCase().contains(query) || (t.artist != null && t.artist.toLowerCase().contains(query))
                        || (t.album != null && t.album.toLowerCase().contains(query))) f.add(t);
            }
            all = f;
        }
        Library.sort(all, Store.sort(this));
        shown = all;
        songs.setData(all);
        songs.setCurrent(Pb.currentId());
        chipSongs.setText(getString(R.string.tab_songs) + " · " + all.size());
        chipLists.setText(getString(R.string.tab_playlists) + " · " + Store.playlists(this).size());
        rv.setAdapter(tab == 0 ? songs : lists);
        lists.notifyDataSetChanged();
        boolean audio = Perms.hasAudio(this);
        if (tab == 0 && all.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            empty.setText(!audio ? R.string.no_access : Library.loaded ? R.string.no_songs : R.string.scanning);
            empty.setOnClickListener(!audio ? v -> {
                Perms.askAudio(this);
            } : null);
        } else {
            empty.setVisibility(View.GONE);
        }
        banner.setVisibility(Perms.anyMissing(this) ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Pb.connect(this);
        Pb.add(current);
        mini.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Perms.hasAudio(this) && (!Library.loaded || Library.tracks.isEmpty())) rescan();
        else refresh();
    }

    @Override
    protected void onStop() {
        Pb.remove(current);
        mini.stop();
        super.onStop();
    }

    @Override
    public void onClick(Track t, int pos) {
        Pb.play(this, shown, pos, false);
    }

    @Override
    public void onMore(Track t, int pos) {
        Menus.song(this, t, this::refresh);
    }

    /** Playlist rows; the first (favorites) is always there. */
    private final class PlaylistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            FrameLayout f = new FrameLayout(p.getContext());
            f.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(f) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            final Store.Playlist pl = Store.playlists(MainActivity.this).get(pos);
            ViewGroup f = (ViewGroup) h.itemView;
            f.removeAllViews();
            View row = Ui.settingRow(MainActivity.this, Store.FAV.equals(pl.id) ? R.drawable.ic_heart_fill : R.drawable.ic_list,
                    pl.name, getString(R.string.songs_count, Library.resolve(pl.ids).size()), null, false, v -> {
                        Intent i = new Intent(MainActivity.this, PlaylistActivity.class);
                        i.putExtra("pid", pl.id);
                        startActivity(i);
                    });
            int n = getItemCount();
            Ui.shape(MainActivity.this, row.findViewById(R.id.card), pos == 0, pos == n - 1, R.color.surface);
            f.addView(row);
        }

        @Override
        public int getItemCount() {
            return Store.playlists(MainActivity.this).size();
        }
    }
}
