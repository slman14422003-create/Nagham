package com.nagham.player;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

/**
 * Shared now-playing widgets (cover, title, seek bar, transport) for the player and the lock screen.
 * lock = centered text, rounder cover, and only previous / play / next.
 */
public final class NowPlaying implements Player.Listener {
    public interface ArtCb {
        void onArt(Uri uri);
    }

    private final Context c;
    private final int artPx;
    public final ImageView art;
    public final LinearLayout info, seekBlock, controls;
    private final TextView title, artist, cur, total;
    private final SeekBar seek;
    private final ImageButton play, shuffle, repeat;
    private boolean drag;
    /** After letting go, ignore the player's old position until it has actually jumped (no flicker back). */
    private long pendingSeek = -1, pendingUntil;
    private String lastArt = "\u0000";
    public ArtCb artCb;
    public Runnable onRefresh;
    /** Set while the cover is being swiped, so the track-change pop does not fight the swipe animation. */
    public boolean quietArt;
    private float coverTarget = -1f;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean started, ticking;
    private Boolean lastPlaying;
    private long lastDur = -1, lastSec = -1;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ticking = false;
            progress();
            schedule();
        }
    };

    /** The seek bar only needs frames while music is actually playing. */
    private void schedule() {
        MediaController m = Pb.get();
        if (started && !ticking && m != null && m.isPlaying()) {
            ticking = true;
            h.postDelayed(tick, 250);
        }
    }

    public NowPlaying(Context ctx, int artSizeDp, boolean lock) {
        c = ctx;
        artPx = Ui.dp(c, artSizeDp);
        art = Ui.artView(c, artSizeDp, lock ? 36 : 32);

        info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        title = Ui.text(c, "", lock ? 22 : 26, R.color.text_primary);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLineSpacing(0, 1.05f);
        artist = Ui.text(c, "", 15, R.color.text_secondary);
        artist.setSingleLine(true);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        artist.setPadding(0, Ui.dp(c, 4), 0, 0);
        if (lock) {
            title.setGravity(Gravity.CENTER);
            artist.setGravity(Gravity.CENTER);
        }
        info.addView(title, Ui.lp(-1, -2));
        info.addView(artist, Ui.lp(-1, -2));

        seekBlock = new LinearLayout(c);
        seekBlock.setOrientation(LinearLayout.VERTICAL);
        seekBlock.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        seek = new SeekBar(c);
        seek.setProgressDrawable(ContextCompat.getDrawable(c, R.drawable.seek_progress));
        seek.setThumb(ContextCompat.getDrawable(c, R.drawable.seek_thumb));
        seek.setSplitTrack(false);
        seek.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) cur.setText(Fmt.time(p));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                drag = true;
                pendingSeek = -1;
                Ui.tap(s);
                cur.setTextColor(Ui.color(c, R.color.accent_text));
                cur.animate().scaleX(1.18f).scaleY(1.18f).setDuration(120).start();
                s.animate().scaleY(1.35f).setDuration(120).start();
                if (s.getParent() != null) s.getParent().requestDisallowInterceptTouchEvent(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                drag = false;
                cur.setTextColor(Ui.color(c, R.color.text_secondary));
                cur.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                s.animate().scaleY(1f).setDuration(160).start();
                MediaController m = Pb.get();
                if (m != null) {
                    long to = s.getProgress();
                    pendingSeek = to;
                    pendingUntil = android.os.SystemClock.uptimeMillis() + 900;
                    m.seekTo(to);
                }
            }
        });
        // a bigger, forgiving touch area; the finger can wander off the bar vertically without losing the drag
        seek.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });
        LinearLayout times = new LinearLayout(c);
        times.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        cur = Ui.text(c, "0:00", 12, R.color.text_secondary);
        total = Ui.text(c, "0:00", 12, R.color.text_secondary);
        total.setGravity(Gravity.END);
        times.addView(cur, Ui.weight(1));
        times.addView(total, Ui.weight(1));
        seekBlock.addView(seek, Ui.lp(-1, Ui.dp(c, 44)));
        seekBlock.addView(times, Ui.lp(-1, -2));

        controls = new LinearLayout(c);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        shuffle = Ui.flat(c, R.drawable.ic_shuffle, 48, R.string.shuffle, v -> {
            MediaController m = Pb.get();
            if (m != null) m.setShuffleModeEnabled(!m.getShuffleModeEnabled());
        });
        ImageButton prev = Ui.flat(c, R.drawable.ic_prev, 60, R.string.previous, v -> {
            MediaController m = Pb.get();
            if (m != null) m.seekToPrevious();
        });
        play = Ui.bigPlay(c, v -> {
            MediaController m = Pb.get();
            if (m == null) return;
            if (m.getPlayWhenReady() && m.getPlaybackState() != Player.STATE_ENDED) {
                m.pause();
            } else {
                if (m.getPlaybackState() == Player.STATE_ENDED) m.seekToDefaultPosition();
                m.play();
            }
        });
        ImageButton next = Ui.flat(c, R.drawable.ic_next, 60, R.string.next, v -> {
            MediaController m = Pb.get();
            if (m != null) m.seekToNext();
        });
        repeat = Ui.flat(c, R.drawable.ic_repeat, 48, R.string.repeat, v -> {
            MediaController m = Pb.get();
            if (m == null) return;
            int r = m.getRepeatMode();
            m.setRepeatMode(r == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL
                    : r == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        });
        View[] set = lock ? new View[]{prev, play, next} : new View[]{shuffle, prev, play, next, repeat};
        for (int i = 0; i < set.length; i++) {
            if (i > 0) controls.addView(Ui.space(c, 1f));
            controls.addView(set[i]);
        }
        Art.show(art, null, artPx);
    }

    public void start() {
        started = true;
        Pb.add(this);
        Pb.whenReady(this::refresh);
    }

    public void stop() {
        started = false;
        ticking = false;
        Pb.remove(this);
        h.removeCallbacks(tick);
    }

    @Override
    public void onEvents(Player p, Player.Events e) {
        refresh();
    }

    public void refresh() {
        MediaController m = Pb.get();
        if (m == null) return;
        MediaItem it = m.getCurrentMediaItem();
        MediaMetadata md = it != null ? it.mediaMetadata : MediaMetadata.EMPTY;
        title.setText(it == null || md.title == null ? c.getString(R.string.nothing_playing) : md.title);
        artist.setText(it == null ? "" : Fmt.artist(c, md.artist == null ? null : md.artist.toString()));
        boolean playing = m.getPlayWhenReady() && m.getPlaybackState() != Player.STATE_ENDED;
        play.setImageResource(playing ? R.drawable.ic_pause_fill : R.drawable.ic_play_fill);
        if (lastPlaying != null && lastPlaying != playing) Ui.pop(play);
        lastPlaying = playing;
        float tgt = playing ? 1f : 0.9f;
        if (coverTarget < 0f) {
            coverTarget = tgt;
            art.setScaleX(tgt);
            art.setScaleY(tgt);
        } else if (tgt != coverTarget) {
            coverTarget = tgt;
            art.animate().scaleX(tgt).scaleY(tgt).setDuration(320)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }
        Ui.tint(shuffle, m.getShuffleModeEnabled() ? R.color.accent_text : R.color.text_secondary);
        int rm = m.getRepeatMode();
        repeat.setImageResource(rm == Player.REPEAT_MODE_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        Ui.tint(repeat, rm == Player.REPEAT_MODE_OFF ? R.color.text_secondary : R.color.accent_text);
        Uri au = md.artworkUri;
        String key = au == null ? "" : au.toString();
        if (!key.equals(lastArt)) {
            boolean first = "\u0000".equals(lastArt);
            lastArt = key;
            Art.load(c, au, art, artPx);
            if (!first && !quietArt) {
                art.setAlpha(0.35f);
                art.setScaleX(coverTarget * 0.85f);
                art.setScaleY(coverTarget * 0.85f);
                art.animate().alpha(1f).scaleX(coverTarget).scaleY(coverTarget).setDuration(380)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
            }
            if (artCb != null) artCb.onArt(au);
        }
        progress();
        schedule();
        if (onRefresh != null) onRefresh.run();
    }

    private void progress() {
        MediaController m = Pb.get();
        if (m == null) return;
        long d = m.getDuration();
        if (d == C.TIME_UNSET || d < 0) d = 0;
        if (d != lastDur) {
            lastDur = d;
            seek.setMax((int) d);
            total.setText(Fmt.time(d));
        }
        seek.setEnabled(d > 0);
        if (!drag) {
            long p = m.getCurrentPosition();
            if (pendingSeek >= 0) {
                boolean landed = Math.abs(p - pendingSeek) < 1200;
                if (!landed && android.os.SystemClock.uptimeMillis() < pendingUntil) return;
                pendingSeek = -1;
            }
            seek.setProgress((int) p, true);
            long sec = p / 1000;
            if (sec != lastSec) {
                lastSec = sec;
                cur.setText(Fmt.time(p));
            }
        }
    }
}
