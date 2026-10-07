package com.simomusic.player;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Home: header, segmented Songs / Playlists, scrolling list with play-all header, permission banner, mini player. */
public class MainActivity extends AppCompatActivity implements TrackAdapter.Listener, FullBleed {
    /** Set by the media notification and by the lock screen: open the app with the full player already up. */
    public static final String ACTION_OPEN_PLAYER = "com.simomusic.player.OPEN_PLAYER";

    /** An intent that brings the app to the front (reusing the running one) with the full player expanded. */
    public static Intent openPlayerIntent(android.content.Context c) {
        return new Intent(c, MainActivity.class).setAction(ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private TrackAdapter songs;
    private final PlaylistAdapter lists = new PlaylistAdapter();
    private RecyclerView rv;
    private TextView segSongs, segLists, subtitle, emptyText;
    private LinearLayout emptyBox, skeleton;
    private android.animation.ObjectAnimator pulse;
    private Button grant;
    private View actions, banner;
    private EditText search;
    private MiniPlayer mini;
    private PlayerPanel panel;
    private LinearLayout mainCol;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout srl;
    private androidx.activity.OnBackPressedCallback backCb;
    private int tab = 0;
    private boolean animateNext = true;
    private String query = "";
    private List<Track> shown = new ArrayList<>();

    private final ActivityResultLauncher<String[]> askPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> rescan());

    private final Player.Listener current = new Player.Listener() {
        @Override
        public void onMediaItemTransition(MediaItem item, int reason) {
            songs.setCurrent(Pb.currentId());
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            songs.setPlaying(isPlaying);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        splash.setOnExitAnimationListener(p -> p.getView().animate().alpha(0f).scaleX(1.14f).scaleY(1.14f).setDuration(260)
                .setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(p::remove).start());
        super.onCreate(b);
        Pb.connect(this);
        build();
        if (!Perms.hasAudio(this) && !Store.flag(this, "first_done", false)) {
            Store.setFlag(this, "first_done", true);
            askPerms.launch(Perms.firstRun());
        } else {
            rescan();
        }
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIntent(i);
    }

    /** Notification / lock-screen tap: show the home screen and slide the full player up over it. */
    private void handleIntent(Intent i) {
        if (i == null || !ACTION_OPEN_PLAYER.equals(i.getAction())) return;
        i.setAction(null);          // consumed: a rotation or theme change must not pop the player up again
        final android.view.View decor = getWindow().getDecorView();
        decor.post(() -> {
            try {
                if (panel == null || isFinishing() || isDestroyed()) return;
                Pb.connect(this);
                panel.setCollapsedY(Math.max(1, decor.getHeight()));
                // wait for the connection so an empty queue never shows a blank player
                Pb.whenReady(() -> {
                    try {
                        if (panel != null && Pb.hasMedia() && !panel.isExpanded()) panel.expand(true);
                    } catch (RuntimeException e) {
                        CrashGuard.nonFatal("open player", e);
                    }
                });
            } catch (RuntimeException e) {
                CrashGuard.nonFatal("open player", e);
            }
        });
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ---- header: title + count, round buttons
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPaddingRelative(Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 4));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, getString(R.string.app_name), 34, R.color.text_primary);
        title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        subtitle = Ui.text(this, "", 13, R.color.text_secondary);
        titles.addView(title);
        titles.addView(subtitle);
        bar.addView(titles, Ui.weight(1));
        View[] btns = {
                Ui.icon(this, R.drawable.ic_search, R.string.search, v -> toggleSearch()),
                Ui.icon(this, R.drawable.ic_sort, R.string.sort_by, v -> Menus.sort(this, this::refresh)),
                Ui.icon(this, R.drawable.ic_settings, R.string.settings, v -> Ui.go(this, new Intent(this, SettingsActivity.class)))};
        for (View v : btns) {
            ((LinearLayout.LayoutParams) v.getLayoutParams()).setMarginStart(Ui.dp(this, 8));
            bar.addView(v);
        }
        root.addView(bar);

        search = Ui.searchEdit(this, getString(R.string.search_hint));
        search.setOnEditorActionListener((v, id, ev) -> {
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
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
                null, false, v -> Ui.go(this, new Intent(this, SettingsActivity.class)));
        banner.setVisibility(View.GONE);
        Ui.shape(this, banner.findViewById(R.id.card), true, true, R.color.accent_soft);
        root.addView(banner);

        // ---- segmented control
        LinearLayout seg = new LinearLayout(this);
        seg.setBackgroundResource(R.drawable.bg_segment);
        seg.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        segSongs = segItem(0);
        segLists = segItem(1);
        seg.addView(segSongs, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f));
        seg.addView(segLists, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 6));
        root.addView(seg, sp);

        // ---- list (the play-all pills are the list header, so everything scrolls together)
        songs = new TrackAdapter(this, this);
        actions = Ui.pillRow(this,
                Ui.pill(this, R.string.play_all, R.drawable.ic_play, true, v -> playAll(false)),
                Ui.pill(this, R.string.shuffle_all, R.drawable.ic_shuffle, false, v -> playAll(true)));
        FrameLayout body = new FrameLayout(this);
        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setClipToPadding(false);
        rv.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setHasFixedSize(true);
        rv.setItemViewCacheSize(12);
        rv.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_fall));
        RecyclerView.ItemAnimator ia = rv.getItemAnimator();
        if (ia instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) ia).setSupportsChangeAnimations(false);
        }
        srl = new androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this);
        srl.addView(rv, new ViewGroup.LayoutParams(-1, -1));
        srl.setProgressBackgroundColorSchemeColor(Ui.color(this, R.color.surface_high));
        srl.setColorSchemeColors(Ui.color(this, R.color.accent_text));
        srl.setOnRefreshListener(() -> Library.scan(this, () -> {
            srl.setRefreshing(false);
            refresh();
        }));
        body.addView(srl, new FrameLayout.LayoutParams(-1, -1));
        skeleton = Ui.skeleton(this);
        skeleton.setVisibility(View.GONE);
        body.addView(skeleton, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        pulse = android.animation.ObjectAnimator.ofFloat(skeleton, "alpha", 0.45f, 1f);
        pulse.setDuration(850);
        pulse.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        pulse.setRepeatMode(android.animation.ObjectAnimator.REVERSE);

        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.setPadding(Ui.dp(this, 36), 0, Ui.dp(this, 36), 0);
        ImageView ei = new ImageView(this);
        ei.setImageResource(R.drawable.ic_music);
        Ui.tint(ei, R.color.text_hint);
        emptyBox.addView(ei, Ui.lp(Ui.dp(this, 56), Ui.dp(this, 56)));
        emptyText = Ui.text(this, "", 15, R.color.text_secondary);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams tp = Ui.lp(-1, -2);
        tp.topMargin = Ui.dp(this, 14);
        emptyBox.addView(emptyText, tp);
        grant = Ui.pill(this, R.string.allow_access, 0, true, v -> Perms.askAudio(this));
        LinearLayout.LayoutParams gp = Ui.lp(-2, -2);
        gp.topMargin = Ui.dp(this, 18);
        emptyBox.addView(grant, gp);
        body.addView(emptyBox, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        mini = new MiniPlayer(this);
        root.addView(mini);
        mainCol = root;

        // the full player lives on top of the home screen and follows the finger (see PlayerPanel)
        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Ui.color(this, R.color.bg));
        stage.addView(root, new FrameLayout.LayoutParams(-1, -1));
        panel = new PlayerPanel(this, new PlayerPanel.Host() {
            @Override
            public void onCollapsed() {
                setMainLayer(false);
                mainCol.setScaleX(1f);
                mainCol.setScaleY(1f);
                mainCol.setAlpha(1f);
            }

            @Override
            public void onProgress(float f) {
                setMainLayer(f > 0.001f && f < 0.999f);
                mainCol.setScaleX(1f - 0.05f * f);
                mainCol.setScaleY(1f - 0.05f * f);
                mainCol.setAlpha(1f - 0.5f * f);
            }

            @Override
            public void onState(boolean expanded) {
                backCb.setEnabled(expanded);
            }
        });
        stage.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        mini.setPanel(panel);
        setContentView(stage);
        getWindow().setBackgroundDrawable(Ui.glow(this));
        Ui.edgeToEdge(this, root);
        backCb = new androidx.activity.OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                panel.collapse(true);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCb);
        setTab(0);
    }

    private boolean mainLayer;

    /** Scale + fade of the whole home screen is smooth only when it is drawn once into a layer. */
    private void setMainLayer(boolean on) {
        if (on == mainLayer || mainCol == null) return;
        mainLayer = on;
        mainCol.setLayerType(on ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
    }

    private TextView segItem(final int i) {
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(14);
        t.setSingleLine(true);
        t.setOnClickListener(v -> {
            if (tab != i) Ui.tap(v);
            setTab(i);
        });
        Ui.press(this, t);
        return t;
    }

    private void styleSeg(TextView t, int labelRes, int count, boolean sel) {
        String label = getString(labelRes);
        SpannableString s = new SpannableString(label + "  " + count);
        s.setSpan(new ForegroundColorSpan(sel ? ((Ui.color(this, R.color.on_accent) & 0x00FFFFFF) | 0xB3000000) : Ui.color(this, R.color.text_hint)), label.length(), s.length(), 0);
        t.setText(s);
        t.setTextColor(Ui.color(this, sel ? R.color.on_accent : R.color.text_secondary));
        t.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        if (sel) t.setBackgroundResource(R.drawable.bg_segment_sel);
        else t.setBackground(null);
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
        refresh();
    }

    private void playAll(boolean shuffle) {
        if (shown.isEmpty()) return;
        Pb.play(this, shown, shuffle ? -1 : 0, shuffle);
    }

    private void rescan() {
        CrashGuard.offer(this);
        Library.scan(this, () -> {
            Resume.restore(this);
            refresh();
        });
        refresh();
    }

    private void refresh() {
        List<Track> all = Library.sorted(Store.sort(this));
        int total = Library.tracks.size();
        if (!query.isEmpty()) {
            List<Track> f = new ArrayList<>();
            for (Track t : all) if (t.key.contains(query)) f.add(t);
            all = f;
        }
        shown = all;
        songs.header = all.isEmpty() ? null : actions;
        songs.setData(all);
        songs.setCurrent(Pb.currentId());

        subtitle.setText(getResources().getQuantityString(R.plurals.songs_n, total, total));
        styleSeg(segSongs, R.string.tab_songs, total, tab == 0);
        styleSeg(segLists, R.string.tab_playlists, Store.playlists(this).size(), tab == 1);
        RecyclerView.Adapter<?> target = tab == 0 ? songs : lists;
        boolean swapped = rv.getAdapter() != target;
        if (swapped) rv.setAdapter(target);
        if (tab == 1) lists.notifyDataSetChanged();
        if ((swapped || animateNext) && target.getItemCount() > 0) {
            animateNext = false;
            rv.scheduleLayoutAnimation();
        }

        boolean audio = Perms.hasAudio(this);
        boolean loading = tab == 0 && audio && !Library.loaded;
        skeleton.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            if (!pulse.isStarted()) pulse.start();
        } else {
            pulse.cancel();
        }
        songs.setPlaying(Pb.get() != null && Pb.get().isPlaying());
        if (tab == 0 && all.isEmpty() && !loading) {
            emptyBox.setVisibility(View.VISIBLE);
            emptyText.setText(!audio ? R.string.no_access : R.string.no_songs);
            grant.setVisibility(audio ? View.GONE : View.VISIBLE);
        } else {
            emptyBox.setVisibility(View.GONE);
        }
        banner.setVisibility(Perms.anyMissing(this) ? View.VISIBLE : View.GONE);
        srl.setEnabled(tab == 0 && audio);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Pb.connect(this);
        Pb.add(current);
        mini.start();
        panel.onHostStart();
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
        panel.onHostStop();
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

    /** Row 0 is "New playlist", then Favorites and the user's playlists. */
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
            ViewGroup f = (ViewGroup) h.itemView;
            f.removeAllViews();
            int n = getItemCount();
            View row;
            if (pos == 0) {
                row = Ui.settingRow(MainActivity.this, R.drawable.ic_add, getString(R.string.new_playlist), null, null, false,
                        v -> Menus.ask(MainActivity.this, R.string.new_playlist, "", name -> {
                            Store.create(MainActivity.this, name);
                            refresh();
                        }));
                Ui.shape(MainActivity.this, row.findViewById(R.id.card), true, n == 1, R.color.accent_soft);
            } else {
                final Store.Playlist pl = Store.playlists(MainActivity.this).get(pos - 1);
                int cnt = Library.count(pl.ids);
                row = Ui.settingRow(MainActivity.this, Store.FAV.equals(pl.id) ? R.drawable.ic_heart_fill : R.drawable.ic_list,
                        pl.name, getResources().getQuantityString(R.plurals.songs_n, cnt, cnt), null, false, v -> {
                            Intent i = new Intent(MainActivity.this, PlaylistActivity.class);
                            i.putExtra("pid", pl.id);
                            Ui.go(MainActivity.this, i);
                        });
                Ui.shape(MainActivity.this, row.findViewById(R.id.card), false, pos == n - 1, R.color.surface);
            }
            f.addView(row);
        }

        @Override
        public int getItemCount() {
            return Store.playlists(MainActivity.this).size() + 1;
        }
    }
}
