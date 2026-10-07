package com.simomusic.player;

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
    private final ImageButton play;
    /** Not part of the transport row: the full player puts them in its bottom row, close to the thumb. */
    public final ImageButton shuffle, repeat;
    /** Hosts (player, lock screen) get the colour picked from the cover; 0 = grey / no cover. */
    public java.util.function.IntConsumer onTint;
    private int tintShown = 0xFFFFFFFF;
    private android.animation.ValueAnimator tintAnim;
    private TextView upNext;
    private boolean remain;
    private boolean drag;
    private long lastTick;
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
        title.setTypeface(Ui.titleFace(true));
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLineSpacing(0, 1.05f);
        // long titles shrink to fit two lines instead of being cut with "..."
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(title, 17, lock ? 22 : 26, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
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
        if (!lock) {
            upNext = Ui.text(c, "", 13, R.color.accent_text);
            upNext.setSingleLine(true);
            upNext.setEllipsize(android.text.TextUtils.TruncateAt.END);
            upNext.setPadding(0, Ui.dp(c, 8), 0, 0);
            upNext.setVisibility(View.GONE);
            info.addView(upNext, Ui.lp(-1, -2));
        }

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
                if (user) {
                    long step = p / 5000;       // a light tick every 5 seconds the thumb passes, like scrubbing a ruler
                    if (step != lastTick) {
                        lastTick = step;
                        Ui.tick(s);
                    }
                    cur.setText(Fmt.time(p));
                    if (remain) total.setText("-" + Fmt.time(Math.max(0, s.getMax() - p)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                drag = true;
                pendingSeek = -1;
                lastTick = s.getProgress() / 5000;
                Ui.gestureStart(s);
                cur.setTextColor(Ui.color(c, R.color.accent_text));
                cur.animate().scaleX(1.18f).scaleY(1.18f).setDuration(120).start();
                s.animate().scaleY(1.35f).setDuration(120).start();
                if (s.getParent() != null) s.getParent().requestDisallowInterceptTouchEvent(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                drag = false;
                Ui.gestureEnd(s);
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
        total.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        remain = Store.flag(c, "remain", false);
        // tap the total time to switch between the song length and the time left
        total.setOnClickListener(v -> {
            remain = !remain;
            Store.setFlag(c, "remain", remain);
            Ui.tap(v);
            lastDur = -1;
            lastSec = -1;
            progress();
        });
        times.addView(cur, Ui.weight(1));
        times.addView(total, Ui.weight(1));
        seekBlock.addView(seek, Ui.lp(-1, Ui.dp(c, 44)));
        seekBlock.addView(times, Ui.lp(-1, -2));

        controls = new LinearLayout(c);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        // previous / play / next sit together in the middle, a thumb's width apart, instead of spreading over the whole
        // screen width: every one of them is reachable with one hand without moving the grip
        controls.setGravity(Gravity.CENTER);
        shuffle = Ui.flat(c, R.drawable.ic_shuffle, 48, R.string.shuffle, v -> {
            MediaController m = Pb.get();
            if (m != null) m.setShuffleModeEnabled(!m.getShuffleModeEnabled());
        });
        ImageButton prev = Ui.flat(c, R.drawable.ic_prev, 64, R.string.previous, v -> {
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
        ImageButton next = Ui.flat(c, R.drawable.ic_next, 64, R.string.next, v -> {
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
        View[] set = {prev, play, next};
        for (int i = 0; i < set.length; i++) {
            if (i > 0) controls.addView(new android.widget.Space(c), new LinearLayout.LayoutParams(Ui.dp(c, 14), 1));
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
        try {
            refreshInner();
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("now playing", e);
        }
    }

    private void refreshInner() {
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
        if (upNext != null) {
            int ni = m.getNextMediaItemIndex();
            if (ni >= 0 && ni < m.getMediaItemCount() && ni != m.getCurrentMediaItemIndex()) {
                CharSequence nt = m.getMediaItemAt(ni).mediaMetadata.title;
                upNext.setText(c.getString(R.string.up_next) + ": " + (nt == null ? "" : nt));
                upNext.setVisibility(View.VISIBLE);
            } else {
                upNext.setVisibility(View.GONE);
            }
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
            tintFromArt(au, key);
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
        try {
            progressInner();
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("seek bar", e);
        }
    }

    // ---- colour from the cover: tints the seek bar here and the glow in the host
    private void tintFromArt(Uri au, final String key) {
        if (au == null) {
            applyTint(0);
            return;
        }
        Art.fetch(c, au, artPx, bmp -> {
            if (!key.equals(lastArt)) return;
            applyTint(bmp == null ? 0 : colorOf(bmp));
        });
    }

    private static int colorOf(android.graphics.Bitmap b) {
        try {
            android.graphics.Bitmap sm = android.graphics.Bitmap.createScaledBitmap(b, 24, 24, true);
            int[] px = new int[24 * 24];
            sm.getPixels(px, 0, 24, 0, 0, 24, 24);
            if (sm != b) sm.recycle();
            return ArtColor.dominant(px, px.length);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void applyTint(int color) {
        if (onTint != null) onTint.accept(color);
        int target = color == 0 ? 0xFFFFFFFF : color;
        if (tintAnim != null) tintAnim.cancel();
        tintAnim = android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), tintShown, target);
        tintAnim.setDuration(500);
        tintAnim.addUpdateListener(a -> {
            tintShown = (Integer) a.getAnimatedValue();
            seek.setProgressTintList(android.content.res.ColorStateList.valueOf(tintShown));
        });
        tintAnim.start();
    }

    private void progressInner() {
        MediaController m = Pb.get();
        if (m == null) return;
        long d = m.getDuration();
        if (d == C.TIME_UNSET || d < 0) d = 0;
        if (d != lastDur) {
            lastDur = d;
            seek.setMax((int) d);
            total.setText(remain ? "-" + Fmt.time(d) : Fmt.time(d));
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
                if (remain) total.setText("-" + Fmt.time(Math.max(0, d - p)));
            }
        }
    }
}
