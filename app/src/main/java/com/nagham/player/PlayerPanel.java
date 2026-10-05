package com.nagham.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.session.MediaController;

/**
 * The full player as a sheet that follows your finger. Drag the mini player up (or tap it) to fill the screen, drag the
 * player down to collapse it; swipe the cover left / right for next / previous. The same view is used full-screen by
 * PlayerActivity (opened from the notification or the lock screen), where "collapse" closes the activity.
 */
public final class PlayerPanel extends FrameLayout {
    public interface Host {
        /** Panel is fully hidden again. */
        void onCollapsed();

        /** 0 = collapsed ... 1 = fills the screen (use it to dim / scale what is behind). */
        void onProgress(float f);

        void onState(boolean expanded);
    }

    private final Activity act;
    private final Host host;
    private final NowPlaying np;
    private final ImageButton fav, timer;
    private final LinearLayout col;
    private final int slop, maxRadius;
    private float collapsedY = 1000f, radius, downX, downY, startRaw;
    private boolean expanded, tracking, onSeek;
    private ValueAnimator anim;
    private VelocityTracker vt;

    @SuppressLint("ClickableViewAccessibility")
    public PlayerPanel(Activity a, Host h) {
        super(a);
        act = a;
        host = h;
        slop = ViewConfiguration.get(a).getScaledTouchSlop();
        maxRadius = Ui.dp(a, 28);
        radius = maxRadius;
        setBackgroundResource(R.drawable.bg_default_backdrop);
        setVisibility(GONE);
        setClickable(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                // extend below the view so only the top corners are rounded
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight() + (int) radius, radius);
            }
        });
        setClipToOutline(true);

        np = new NowPlaying(a, 320, false);
        final ImageView bg = Backdrop.view(a);
        addView(bg, new LayoutParams(-1, -1));
        View scrim = new View(a);
        scrim.setBackgroundResource(R.drawable.bg_scrim);
        addView(scrim, new LayoutParams(-1, -1));

        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(a, 22), Ui.dp(a, 8), Ui.dp(a, 22), Ui.dp(a, 18));

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.icon(a, R.drawable.ic_arrow_down, R.string.close, v -> collapse(true)));
        TextView cap = Ui.text(a, a.getString(R.string.now_playing), 14, R.color.text_secondary);
        cap.setGravity(Gravity.CENTER);
        cap.setTypeface(Typeface.DEFAULT_BOLD);
        cap.setLetterSpacing(0.04f);
        top.addView(cap, Ui.weight(1));
        top.addView(Ui.icon(a, R.drawable.ic_more, R.string.more, v -> Menus.more(a, this::refreshExtras)));
        col.addView(top, Ui.lp(-1, -2));

        CoverBox cover = new CoverBox(a, 340);
        cover.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 14));
        cover.setCover(np.art);
        col.addView(cover, new LinearLayout.LayoutParams(-1, 0, 1f));
        attachCoverSwipe(np.art);

        LinearLayout titleRow = new LinearLayout(a);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(np.info, Ui.weight(1));
        fav = Ui.flat(a, R.drawable.ic_heart, 52, R.string.favorite, v -> {
            long id = Pb.currentId();
            if (id >= 0) Store.toggle(a, Store.FAV, id);
            heart(true);
        });
        titleRow.addView(fav);
        LinearLayout.LayoutParams tp = Ui.lp(-1, -2);
        tp.setMargins(Ui.dp(a, 4), Ui.dp(a, 8), 0, 0);
        col.addView(titleRow, tp);

        LinearLayout.LayoutParams sp = Ui.lp(-1, -2);
        sp.topMargin = Ui.dp(a, 10);
        col.addView(np.seekBlock, sp);
        LinearLayout.LayoutParams cp = Ui.lp(-1, -2);
        cp.topMargin = Ui.dp(a, 4);
        col.addView(np.controls, cp);

        LinearLayout extras = new LinearLayout(a);
        extras.setGravity(Gravity.CENTER);
        timer = Ui.icon(a, R.drawable.ic_timer, R.string.sleep_timer, v -> Menus.sleep(a, this::refreshExtras));
        View[] ex = {
                Ui.icon(a, R.drawable.ic_add, R.string.add_to_playlist, v -> {
                    long id = Pb.currentId();
                    if (id >= 0) Menus.addToPlaylist(a, id, () -> heart(false));
                }),
                Ui.icon(a, R.drawable.ic_list, R.string.up_next, v -> Menus.queue(a)),
                timer};
        for (View v : ex) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) v.getLayoutParams();
            p.width = Ui.dp(a, 52);
            p.height = Ui.dp(a, 52);
            p.setMargins(Ui.dp(a, 10), 0, Ui.dp(a, 10), 0);
            extras.addView(v);
        }
        LinearLayout.LayoutParams xp = Ui.lp(-1, -2);
        xp.topMargin = Ui.dp(a, 18);
        col.addView(extras, xp);

        addView(col, new LayoutParams(-1, -1));
        Ui.edgeToEdge(a, col);
        np.onRefresh = this::refreshExtras;
        np.artCb = u -> Backdrop.set(a, bg, u);
    }

    // ------------------------------------------------------------------ public API

    public void setCollapsedY(float y) {
        collapsedY = Math.max(1f, y);
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** Call from the activity's onStart / onStop. */
    public void onHostStart() {
        if (getVisibility() == VISIBLE) np.start();
    }

    public void onHostStop() {
        np.stop();
    }

    /** Mini player drag: panel appears at the mini player and follows the finger upward. */
    public void beginDrag() {
        if (anim != null) anim.cancel();
        setVisibility(VISIBLE);
        col.requestApplyInsets();
        setTranslationY(collapsedY);
        np.start();
        applyProgress();
    }

    public void dragUp(float dist) {
        setTranslationY(Math.max(0f, collapsedY - dist));
        applyProgress();
    }

    public void endDrag(float vy) {
        float f = 1f - getTranslationY() / collapsedY;
        if (vy < -900f || (vy < 900f && f > 0.35f)) expand(true);
        else collapse(true);
    }

    public void expand(boolean animate) {
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            col.requestApplyInsets();
            setTranslationY(collapsedY);
            applyProgress();
        }
        expanded = true;
        host.onState(true);
        np.start();
        if (animate) {
            animateTo(0f, 380, null);
        } else {
            setTranslationY(0f);
            applyProgress();
        }
    }

    public void collapse(boolean animate) {
        expanded = false;
        host.onState(false);
        final Runnable end = () -> {
            setVisibility(GONE);
            np.stop();
            host.onCollapsed();
        };
        if (animate && getVisibility() == VISIBLE) animateTo(collapsedY, 300, end);
        else {
            setTranslationY(collapsedY);
            applyProgress();
            end.run();
        }
    }

    // ------------------------------------------------------------------ internals

    private void animateTo(float target, long dur, final Runnable end) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(getTranslationY(), target);
        anim.setDuration(dur);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(a -> {
            setTranslationY((Float) a.getAnimatedValue());
            applyProgress();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            boolean canceled;

            @Override
            public void onAnimationCancel(Animator a) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (!canceled && end != null) end.run();
            }
        });
        anim.start();
    }

    /** One place derives everything from how far the sheet is open: fade, corner radius, what is behind. */
    private void applyProgress() {
        float f = 1f - getTranslationY() / collapsedY;
        f = Math.max(0f, Math.min(1f, f));
        setAlpha(Math.min(1f, f / 0.22f));
        col.setAlpha(Math.max(0f, Math.min(1f, (f - 0.18f) / 0.6f)));
        radius = maxRadius * (1f - f);
        invalidateOutline();
        host.onProgress(f);
    }

    private void heart(boolean animate) {
        long id = Pb.currentId();
        boolean on = id >= 0 && Store.has(act, Store.FAV, id);
        fav.setImageResource(on ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        Ui.tint(fav, on ? R.color.accent_text : R.color.text_primary);
        if (animate && on) Ui.pop(fav);
    }

    private void refreshExtras() {
        heart(false);
        Ui.tint(timer, Pb.sleepAt > 0 ? R.color.accent_text : R.color.text_primary);
    }

    private boolean hit(View v, MotionEvent e) {
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        int pad = Ui.dp(act, 10);
        return e.getRawX() >= l[0] && e.getRawX() <= l[0] + v.getWidth()
                && e.getRawY() >= l[1] - pad && e.getRawY() <= l[1] + v.getHeight() + pad;
    }

    // ---- drag the whole sheet down (collapse) ---------------------------------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                onSeek = hit(np.seekBlock, e);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!tracking && expanded && !onSeek) {
                    float dy = e.getRawY() - downY, dx = e.getRawX() - downX;
                    if (dy > slop && dy > Math.abs(dx) * 1.2f) {
                        startTracking(e);
                        return true;
                    }
                }
                break;
            default:
                break;
        }
        return false;
    }

    private void startTracking(MotionEvent e) {
        tracking = true;
        if (anim != null) anim.cancel();
        startRaw = downY;
        vt = VelocityTracker.obtain();
        vt.addMovement(e);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                onSeek = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) {
                    float dy = e.getRawY() - downY, dx = e.getRawX() - downX;
                    if (expanded && dy > slop && dy > Math.abs(dx) * 1.2f) startTracking(e);
                    return true;
                }
                vt.addMovement(e);
                setTranslationY(Math.max(0f, Math.min(collapsedY, e.getRawY() - startRaw)));
                applyProgress();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (tracking) {
                    vt.addMovement(e);
                    vt.computeCurrentVelocity(1000);
                    float vy = vt.getYVelocity();
                    vt.recycle();
                    vt = null;
                    tracking = false;
                    boolean closing = e.getActionMasked() == MotionEvent.ACTION_UP
                            && (vy > 900f || (vy > -900f && getTranslationY() > collapsedY * 0.28f));
                    if (closing) collapse(true);
                    else expand(true);
                }
                return true;
            default:
                return true;
        }
    }

    // ---- swipe the cover: next / previous ---------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private void attachCoverSwipe(View cover) {
        cover.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            boolean drag;
            VelocityTracker t;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX();
                        y0 = e.getRawY();
                        drag = false;
                        if (t != null) t.recycle();
                        t = VelocityTracker.obtain();
                        t.addMovement(e);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (t == null) return false;
                        t.addMovement(e);
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (!drag && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                            drag = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        if (drag) {
                            float w = v.getWidth();
                            v.setTranslationX(dx);
                            v.setRotation(dx / w * 10f);
                            v.setAlpha(1f - Math.min(0.55f, Math.abs(dx) / (w * 1.4f)));
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        if (t == null) return false;
                        t.addMovement(e);
                        t.computeCurrentVelocity(1000);
                        float vx = t.getXVelocity();
                        t.recycle();
                        t = null;
                        float dx = e.getRawX() - x0;
                        boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                        if (!drag) {
                            if (up && Math.abs(dx) < slop && Math.abs(e.getRawY() - y0) < slop) togglePlay(v);
                            return true;
                        }
                        float w = v.getWidth();
                        boolean go = up && (Math.abs(dx) > w * 0.28f || (Math.abs(vx) > 1100f && Math.signum(vx) == Math.signum(dx)));
                        if (go) commitSwipe(v, dx < 0);
                        else springBack(v);
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
    }

    private void togglePlay(View v) {
        MediaController m = Pb.get();
        if (m == null) return;
        Ui.tap(v);
        if (m.isPlaying()) m.pause();
        else m.play();
    }

    /** Throws the old cover off the side, switches track, and lets the new cover settle in from the opposite side. */
    private void commitSwipe(final View v, final boolean next) {
        final float w = v.getWidth(), dir = next ? -1f : 1f;
        np.quietArt = true;
        Ui.tap(v);
        v.animate().translationX(dir * w * 1.15f).rotation(dir * 14f).alpha(0f).setDuration(170)
                .setInterpolator(new AccelerateInterpolator()).withEndAction(() -> {
                    MediaController m = Pb.get();
                    if (m != null) {
                        if (next) m.seekToNext();
                        else m.seekToPrevious();
                    }
                    v.setTranslationX(-dir * w * 0.7f);
                    v.setRotation(-dir * 10f);
                    v.animate().translationX(0f).rotation(0f).alpha(1f).setDuration(300)
                            .setInterpolator(new OvershootInterpolator(1.1f)).withEndAction(() -> np.quietArt = false).start();
                }).start();
    }

    private void springBack(View v) {
        v.animate().translationX(0f).rotation(0f).alpha(1f).setDuration(260)
                .setInterpolator(new OvershootInterpolator(1.3f)).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        np.stop();
        super.onDetachedFromWindow();
    }
}
