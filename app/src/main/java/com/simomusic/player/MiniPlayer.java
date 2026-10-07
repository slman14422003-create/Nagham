package com.simomusic.player;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import android.os.Handler;
import android.os.Looper;

/** Rounded mini player docked above the bottom edge; tap to open the full player. */
public final class MiniPlayer extends LinearLayout implements Player.Listener {
    private final ImageView art;
    private final TextView title, artist;
    private final ImageButton play;
    private final ProgressBar bar;
    private String lastArt = "\u0000";
    private final Handler h = new Handler(Looper.getMainLooper());
    private PlayerPanel panel;
    private boolean started, ticking;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ticking = false;
            MediaController m = Pb.get();
            if (m != null) {
                long d = m.getDuration();
                bar.setMax(d == C.TIME_UNSET || d < 0 ? 0 : (int) d);
                bar.setProgress((int) m.getCurrentPosition(), true);
            }
            schedule();
        }
    };

    public void setPanel(PlayerPanel p) {
        panel = p;
    }

    private void schedule() {
        MediaController m = Pb.get();
        if (started && !ticking && m != null && m.isPlaying() && getVisibility() == VISIBLE) {
            ticking = true;
            h.postDelayed(tick, 400);
        }
    }

    public MiniPlayer(final Context c) {
        super(c);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_card);
        Ui.round(this, Ui.dp(c, 28));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 10));
        setLayoutParams(lp);
        setVisibility(GONE);
        Ui.press(c, this);

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        art = Ui.artView(c, 46, 14);
        row.addView(art);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        title = Ui.text(c, "", 15, R.color.text_primary);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        artist = Ui.text(c, "", 12, R.color.text_secondary);
        artist.setSingleLine(true);
        col.addView(title);
        col.addView(artist);
        LinearLayout.LayoutParams cp = Ui.weight(1);
        cp.setMarginStart(Ui.dp(c, 12));
        row.addView(col, cp);
        play = Ui.flat(c, R.drawable.ic_play_fill, 48, R.string.play_pause, v -> {
            MediaController m = Pb.get();
            if (m == null) return;
            if (m.getPlayWhenReady() && m.getPlaybackState() != Player.STATE_ENDED) m.pause();
            else m.play();
        });
        ImageButton next = Ui.flat(c, R.drawable.ic_next, 48, R.string.next, v -> {
            MediaController m = Pb.get();
            if (m != null) m.seekToNext();
        });
        row.addView(play);
        row.addView(next);
        addView(row, new LinearLayout.LayoutParams(-1, -2));

        bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgressDrawable(ContextCompat.getDrawable(c, R.drawable.mini_progress));
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 3));
        bp.setMargins(Ui.dp(c, 18), 0, Ui.dp(c, 18), Ui.dp(c, 8));
        addView(bar, bp);

        setClickable(true);
        setContentDescription(c.getString(R.string.now_playing));
        final int slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop();
        setOnTouchListener(new OnTouchListener() {
            float x0, y0, base;
            boolean vertical, horizontal, moved;
            android.view.VelocityTracker vt;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        setPressed(true);
                        x0 = e.getRawX();
                        y0 = e.getRawY();
                        vertical = horizontal = moved = false;
                        if (vt != null) vt.recycle();
                        vt = android.view.VelocityTracker.obtain();
                        vt.addMovement(e);
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        if (vt == null) return false;
                        vt.addMovement(e);
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                            moved = true;
                            setPressed(false);
                        }
                        if (!vertical && !horizontal) {
                            if (panel != null && dy < 0 && Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                                vertical = true;
                                base = dy;   // the sheet starts exactly under the finger, no jump
                                setPressed(false);
                                getParent().requestDisallowInterceptTouchEvent(true);
                                panel.setCollapsedY(getTop());
                                panel.beginDrag();
                            } else if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                                horizontal = true;
                                setPressed(false);
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        }
                        if (vertical) panel.dragUp(-(dy - base));
                        else if (horizontal) setTranslationX(dx * 0.6f);
                        return true;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL: {
                        setPressed(false);
                        if (vt == null) return false;
                        vt.addMovement(e);
                        vt.computeCurrentVelocity(1000);
                        float vx = vt.getXVelocity(), vy = vt.getYVelocity();
                        vt.recycle();
                        vt = null;
                        boolean up = e.getActionMasked() == android.view.MotionEvent.ACTION_UP;
                        float dx = e.getRawX() - x0;
                        if (vertical) {
                            panel.endDrag(up ? vy : 0f);
                        } else if (horizontal) {
                            boolean go = up && (Math.abs(dx) > Ui.dp(c, 70)
                                    || (Math.abs(vx) > 900f && Math.signum(vx) == Math.signum(dx)));
                            if (go) {
                                MediaController m = Pb.get();
                                boolean ltr = getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;
                                if (m != null) {
                                    if ((dx < 0) == ltr) m.seekToNext();
                                    else m.seekToPrevious();
                                }
                                Ui.tap(MiniPlayer.this);
                            }
                            animate().translationX(0f).setDuration(300)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
                        } else if (up && !moved && panel != null) {
                            panel.setCollapsedY(getTop());
                            panel.expand(true);
                        } else if (up && !moved) {
                            Ui.go(c, new Intent(c, PlayerActivity.class));
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
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

    private void refresh() {
        MediaController m = Pb.get();
        if (m == null || m.getMediaItemCount() == 0) {
            setVisibility(GONE);
            return;
        }
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            setAlpha(0f);
            setTranslationY(Ui.dp(getContext(), 24));
            animate().alpha(1f).translationY(0f).setDuration(420)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f)).start();
        }
        schedule();
        MediaItem it = m.getCurrentMediaItem();
        if (it == null) return;
        title.setText(it.mediaMetadata.title);
        artist.setText(Fmt.artist(getContext(), it.mediaMetadata.artist == null ? null : it.mediaMetadata.artist.toString()));
        boolean playing = m.getPlayWhenReady() && m.getPlaybackState() != Player.STATE_ENDED;
        play.setImageResource(playing ? R.drawable.ic_pause_fill : R.drawable.ic_play_fill);
        String key = String.valueOf(it.mediaMetadata.artworkUri);
        if (!key.equals(lastArt)) {
            lastArt = key;
            Art.load(getContext(), it.mediaMetadata.artworkUri, art, Ui.dp(getContext(), 46));
        }
    }
}
