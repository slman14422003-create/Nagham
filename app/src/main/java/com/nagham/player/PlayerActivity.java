package com.nagham.player;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Full player: slides up from the mini player, cover-colored backdrop, adaptive cover, transport, extras. */
public class PlayerActivity extends AppCompatActivity implements FullBleed {
    private NowPlaying np;
    private ImageButton fav;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.transitions(this, R.anim.slide_up, R.anim.stay, R.anim.stay, R.anim.slide_down);
        Pb.connect(this);
        np = new NowPlaying(this, 320, false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg_default_backdrop);
        final ImageView bg = Backdrop.view(this);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackgroundResource(R.drawable.bg_scrim);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), Ui.dp(this, 18));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.icon(this, R.drawable.ic_arrow_down, R.string.close, v -> finish()));
        TextView cap = Ui.text(this, getString(R.string.now_playing), 14, R.color.text_secondary);
        cap.setGravity(Gravity.CENTER);
        cap.setTypeface(Typeface.DEFAULT_BOLD);
        cap.setLetterSpacing(0.04f);
        top.addView(cap, Ui.weight(1));
        top.addView(new View(this), Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
        col.addView(top, Ui.lp(-1, -2));

        // the cover takes whatever height is left and stays square
        CoverBox cover = new CoverBox(this, 340);
        cover.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        cover.setCover(np.art);
        col.addView(cover, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(np.info, Ui.weight(1));
        fav = Ui.flat(this, R.drawable.ic_heart, 52, R.string.favorite, v -> {
            long id = Pb.currentId();
            if (id >= 0) Store.toggle(this, Store.FAV, id);
            heart(true);
        });
        titleRow.addView(fav);
        LinearLayout.LayoutParams tp = Ui.lp(-1, -2);
        tp.setMargins(Ui.dp(this, 4), Ui.dp(this, 8), 0, 0);
        col.addView(titleRow, tp);

        LinearLayout.LayoutParams sp = Ui.lp(-1, -2);
        sp.topMargin = Ui.dp(this, 10);
        col.addView(np.seekBlock, sp);
        LinearLayout.LayoutParams cp = Ui.lp(-1, -2);
        cp.topMargin = Ui.dp(this, 4);
        col.addView(np.controls, cp);

        LinearLayout extras = new LinearLayout(this);
        extras.setGravity(Gravity.CENTER);
        View[] ex = {
                Ui.icon(this, R.drawable.ic_add, R.string.add_to_playlist, v -> {
                    long id = Pb.currentId();
                    if (id >= 0) Menus.addToPlaylist(this, id, () -> heart(false));
                }),
                Ui.icon(this, R.drawable.ic_list, R.string.up_next, v -> Menus.queue(this)),
                Ui.icon(this, R.drawable.ic_timer, R.string.sleep_timer, v -> Menus.sleep(this)),
                Ui.icon(this, R.drawable.ic_lock, R.string.lock_preview, v -> Menus.openLock(this))};
        for (View v : ex) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) v.getLayoutParams();
            p.width = Ui.dp(this, 52);
            p.height = Ui.dp(this, 52);
            p.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
            extras.addView(v);
        }
        LinearLayout.LayoutParams xp = Ui.lp(-1, -2);
        xp.topMargin = Ui.dp(this, 18);
        col.addView(extras, xp);

        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        Ui.edgeToEdge(this, col);
        np.onRefresh = () -> heart(false);
        np.artCb = u -> Backdrop.set(this, bg, u);

        // content eases in right after the slide
        Ui.enter(cover, 120);
        Ui.enter(titleRow, 200);
        Ui.enter(np.seekBlock, 260);
        Ui.enter(np.controls, 320);
        Ui.enter(extras, 380);
    }

    private void heart(boolean animate) {
        long id = Pb.currentId();
        boolean on = id >= 0 && Store.has(this, Store.FAV, id);
        fav.setImageResource(on ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        Ui.tint(fav, on ? R.color.accent_text : R.color.text_primary);
        if (animate && on) Ui.pop(fav);
    }

    @Override
    public void finish() {
        super.finish();
        Ui.legacyClose(this, R.anim.stay, R.anim.slide_down);
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
