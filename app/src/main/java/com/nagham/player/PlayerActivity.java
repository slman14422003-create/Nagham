package com.nagham.player;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Full now-playing screen: cover, seek bar, transport, favorite / playlist / sleep timer / lock-screen preview. */
public class PlayerActivity extends AppCompatActivity {
    private NowPlaying np;
    private ImageButton fav;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Pb.connect(this);
        int art = Math.max(180, Math.min(320, (int) (getResources().getConfiguration().screenWidthDp * 0.82f)));
        if (getResources().getConfiguration().screenHeightDp < 640) art = Math.min(art, 200);
        np = new NowPlaying(this, art);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        ImageButton more = Ui.icon(this, R.drawable.ic_timer, R.string.sleep_timer, v -> Menus.sleep(this));
        root.addView(Ui.topBar(this, getString(R.string.now_playing), R.drawable.ic_arrow_down, more));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 24), Ui.dp(this, 24));
        col.addView(np.art);
        LinearLayout.LayoutParams ip = Ui.lp(-1, -2);
        ip.topMargin = Ui.dp(this, 28);
        col.addView(np.info, ip);
        LinearLayout.LayoutParams sp = Ui.lp(-1, -2);
        sp.topMargin = Ui.dp(this, 18);
        col.addView(np.seekBlock, sp);
        LinearLayout.LayoutParams cp = Ui.lp(-1, -2);
        cp.topMargin = Ui.dp(this, 10);
        col.addView(np.controls, cp);

        LinearLayout extras = new LinearLayout(this);
        extras.setGravity(Gravity.CENTER);
        fav = Ui.flat(this, R.drawable.ic_heart, 52, R.string.favorite, v -> {
            long id = Pb.currentId();
            if (id >= 0) Store.toggle(this, Store.FAV, id);
            heart();
        });
        ImageButton plist = Ui.flat(this, R.drawable.ic_add, 52, R.string.add_to_playlist, v -> {
            long id = Pb.currentId();
            if (id >= 0) Menus.addToPlaylist(this, id, this::heart);
        });
        ImageButton lock = Ui.flat(this, R.drawable.ic_lock, 52, R.string.lock_preview, v -> Menus.openLock(this));
        for (View v : new View[]{fav, plist, lock}) {
            ((LinearLayout.LayoutParams) v.getLayoutParams()).setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
            extras.addView(v);
        }
        LinearLayout.LayoutParams xp = Ui.lp(-1, -2);
        xp.topMargin = Ui.dp(this, 22);
        col.addView(extras, xp);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        np.onRefresh = this::heart;
    }

    private void heart() {
        long id = Pb.currentId();
        boolean on = id >= 0 && Store.has(this, Store.FAV, id);
        fav.setImageResource(on ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        Ui.tint(fav, on ? R.color.accent_text : R.color.text_primary);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Pb.connect(this);
        np.start();
    }

    @Override
    protected void onStop() {
        np.stop();
        super.onStop();
    }
}
