package com.simomusic.player;

import android.content.Context;
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

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
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
    private final ImageButton fav, timer, bt;
    private final LinearLayout col;
    private final int slop, maxRadius;
    private View parBg, parGlow;   // parallax layers
    private float collapsedY = 1000f, radius, downX, downY, startRaw;
    private boolean expanded, tracking, onSeek;
    private SpringAnimation anim;
    private float releaseVy;      // finger speed at the moment of letting go: the spring carries it on, no sudden stop
    private int barsState = -1;
    private VelocityTracker vt;

    @SuppressLint("ClickableViewAccessibility")
    public PlayerPanel(Activity a, Host h) {
        super(Ui.dark(a));
        final android.content.Context d = getContext();
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

        np = new NowPlaying(d, 320, false);
        final ImageView bg = Backdrop.view(d);
        parBg = bg;
        addView(bg, new LayoutParams(-1, -1));
        ArtGlow glow = new ArtGlow(d);
        addView(glow, new LayoutParams(-1, -1));
        np.onTint = glow::setColor;
        parGlow = glow;
        View scrim = new View(d);
        scrim.setBackgroundResource(R.drawable.bg_scrim);
        addView(scrim, new LayoutParams(-1, -1));

        col = new LinearLayout(d);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(a, 22), Ui.dp(a, 8), Ui.dp(a, 22), Ui.dp(a, 18));

        // grab handle, like every One UI sheet: it says "pull me down", and the whole screen answers to that gesture
        View grab = new View(d);
        android.graphics.drawable.GradientDrawable gh = new android.graphics.drawable.GradientDrawable();
        gh.setColor(0x66FFFFFF);
        gh.setCornerRadius(Ui.dp(a, 3));
        grab.setBackground(gh);
        LinearLayout.LayoutParams ghp = new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 5));
        ghp.gravity = Gravity.CENTER_HORIZONTAL;
        ghp.topMargin = Ui.dp(a, 2);
        col.addView(grab, ghp);

        LinearLayout top = new LinearLayout(d);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.icon(d, R.drawable.ic_arrow_down, R.string.close, v -> collapse(true)));
        TextView cap = Ui.text(d, a.getString(R.string.now_playing), 14, R.color.text_secondary);
        cap.setGravity(Gravity.CENTER);
        cap.setTypeface(Typeface.DEFAULT_BOLD);
        cap.setLetterSpacing(0.04f);
        top.addView(cap, Ui.weight(1));
        top.addView(Ui.icon(d, R.drawable.ic_more, R.string.more, v -> Menus.more(a, this::refreshExtras)));
        col.addView(top, Ui.lp(-1, -2));

        CoverBox cover = new CoverBox(d, 340);
        cover.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 14));
        cover.setCover(np.art);
        col.addView(cover, new LinearLayout.LayoutParams(-1, 0, 1f));
        attachCoverSwipe(np.art);

        LinearLayout titleRow = new LinearLayout(d);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(np.info, Ui.weight(1));
        fav = Ui.flat(d, R.drawable.ic_heart, 52, R.string.favorite, v -> {
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

        // Bottom row, right under the transport buttons (the easiest place for the thumb): shuffle, add, queue, Bluetooth,
        // timer, repeat. Nothing important sits only at the top of the screen.
        LinearLayout extras = new LinearLayout(d);
        extras.setGravity(Gravity.CENTER_VERTICAL);
        timer = Ui.icon(d, R.drawable.ic_timer, R.string.sleep_timer, v -> Menus.sleep(a, this::refreshExtras));
        bt = Ui.icon(d, R.drawable.ic_bluetooth, R.string.bt_title, v -> BtSheet.show(a));
        for (ImageButton b : new ImageButton[]{np.shuffle, np.repeat}) {     // same round look as the other four
            b.setBackgroundResource(R.drawable.bg_icon_btn);
            b.setScaleType(ImageView.ScaleType.CENTER);
            b.setPadding(0, 0, 0, 0);
        }
        View[] ex = {
                np.shuffle,
                Ui.icon(d, R.drawable.ic_add, R.string.add_to_playlist, v -> {
                    long id = Pb.currentId();
                    if (id >= 0) Menus.addToPlaylist(a, id, () -> heart(false));
                }),
                Ui.icon(d, R.drawable.ic_list, R.string.up_next, v -> Menus.queue(a)),
                bt,
                timer,
                np.repeat};
        for (int i = 0; i < ex.length; i++) {
            if (i > 0) extras.addView(Ui.space(d, 1f));
            ex[i].setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
            extras.addView(ex[i]);
        }
        // one compact group in the middle, not stretched over a wide screen
        int avail = a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 44);
        LinearLayout.LayoutParams xp = Ui.lp(Math.min(avail, Ui.dp(a, 340)), -2);
        xp.gravity = Gravity.CENTER_HORIZONTAL;
        xp.topMargin = Ui.dp(a, 16);
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
        layer(true);
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
        releaseVy = vy;
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
            layer(true);
            animateTo(0f, 420, null);
        } else {
            setTranslationY(0f);
            applyProgress();
            layer(false);
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
        if (animate && getVisibility() == VISIBLE) {
            layer(true);
            animateTo(collapsedY, 320, end);
        } else {
            setTranslationY(collapsedY);
            applyProgress();
            layer(false);
            end.run();
        }
    }

    // ------------------------------------------------------------------ internals

    /** While the sheet moves it is drawn once into a layer, so dragging and flinging stay at full frame rate. */
    private void layer(boolean on) {
        int want = on ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE;
        if (getLayerType() != want) setLayerType(want, null);
        // the content column fades and scales while the sheet moves: drawn once into its own layer, not re-composited per frame
        if (col.getLayerType() != want) col.setLayerType(want, null);
    }

    /** The sheet can be grabbed when open, and also while it is still animating (open or closed). */
    private boolean canGrab() {
        return expanded || (getVisibility() == VISIBLE && anim != null && anim.isRunning());
    }

    /**
     * Spring physics instead of a fixed-time curve: the sheet keeps the speed of the finger that threw it, can be caught
     * again at any moment without a jump, and settles the way One UI's own sheets do.
     */
    private void animateTo(final float target, long unusedDuration, final Runnable end) {
        if (anim != null) anim.cancel();
        final SpringAnimation sa = new SpringAnimation(this, DynamicAnimation.TRANSLATION_Y, target);
        sa.getSpring().setStiffness(target == 0f ? 380f : 520f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
        sa.setStartVelocity(releaseVy);
        releaseVy = 0f;
        sa.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
        sa.addUpdateListener((animation, value, velocity) -> applyProgress());
        sa.addEndListener((animation, canceled, value, velocity) -> {
            if (canceled) return;
            setTranslationY(target);
            applyProgress();
            layer(false);
            if (end != null) end.run();
        });
        anim = sa;
        sa.start();
    }

    /** One place derives everything from how far the sheet is open: fade, corner radius, what is behind. */
    private void applyProgress() {
        float f = 1f - getTranslationY() / collapsedY;
        f = Math.max(0f, Math.min(1f, f));
        setAlpha(Math.min(1f, f / 0.22f));
        col.setAlpha(Math.max(0f, Math.min(1f, (f - 0.18f) / 0.6f)));
        float nr = maxRadius * (1f - f);
        if (nr != radius && (Math.abs(nr - radius) > 0.6f || nr < 0.01f || nr > maxRadius - 0.01f)) {
            radius = nr;
            invalidateOutline();
        }
        // parallax: the far layers drift slowly, the content catches up, so the sheet feels like it has depth
        float inv = 1f - f;
        if (parBg != null) parBg.setTranslationY(-inv * Ui.dp(act, 70));
        if (parGlow != null) parGlow.setTranslationY(-inv * Ui.dp(act, 40));
        col.setTranslationY(inv * Ui.dp(act, 26));
        float sc = 0.95f + 0.05f * f;
        col.setScaleX(sc);
        col.setScaleY(sc);
        barsFor(f);
        host.onProgress(f);
    }

    /** The open player is always dark: light status-bar icons while it covers the screen, the theme's own when it is gone. */
    private void barsFor(float f) {
        int want = f > 0.45f ? 1 : 0;
        if (want == barsState) return;
        barsState = want;
        Ui.bars(act, want == 1 || Ui.isNight(act));
    }

    private void heart(boolean animate) {
        long id = Pb.currentId();
        boolean on = id >= 0 && Store.has(act, Store.FAV, id);
        fav.setImageResource(on ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        Ui.tint(fav, on ? R.color.accent_text : R.color.text_primary);
        if (animate && on) {
            Ui.pop(fav);
            Ui.confirm(fav);
        }
    }

    private void refreshExtras() {
        try {
            refreshExtrasInner();
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("player extras", e);
        }
    }

    private void refreshExtrasInner() {
        heart(false);
        Ui.tint(timer, Pb.sleepAt > 0 ? R.color.accent_text : R.color.text_primary);
        Ui.tint(bt, BtAudio.connected(act) ? R.color.accent_text : R.color.text_primary);
    }

    private final android.media.AudioDeviceCallback btWatch = new android.media.AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(android.media.AudioDeviceInfo[] a) {
            refreshExtras();
        }

        @Override
        public void onAudioDevicesRemoved(android.media.AudioDeviceInfo[] r) {
            refreshExtras();
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        android.media.AudioManager am = (android.media.AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) am.registerAudioDeviceCallback(btWatch, new android.os.Handler(android.os.Looper.getMainLooper()));
        refreshExtras();
    }

    private boolean hit(View v, MotionEvent e) {
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        int pad = Ui.dp(act, 14);
        return e.getRawX() >= l[0] && e.getRawX() <= l[0] + v.getWidth()
                && e.getRawY() >= l[1] - pad && e.getRawY() <= l[1] + v.getHeight() + pad;
    }

    // ---- drag the whole sheet down (collapse) ---------------------------------

    private void vtReset(MotionEvent e) {
        if (vt != null) vt.recycle();
        vt = VelocityTracker.obtain();
        vt.addMovement(e);
    }

    private void vtAdd(MotionEvent e) {
        if (vt != null) vt.addMovement(e);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                onSeek = hit(np.seekBlock, e);
                vtReset(e);
                break;
            case MotionEvent.ACTION_MOVE:
                vtAdd(e);
                if (!tracking && canGrab() && !onSeek) {
                    float dy = e.getRawY() - downY, dx = e.getRawX() - downX;
                    if (dy > slop && dy > Math.abs(dx) * 1.1f) {
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
        Ui.gestureStart(this);
        layer(true);
        // keep the sheet exactly under the finger: no jump when it is caught mid-animation
        startRaw = e.getRawY() - getTranslationY();
        if (vt == null) vtReset(e);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                onSeek = false;
                vtReset(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                vtAdd(e);
                if (!tracking) {
                    float dy = e.getRawY() - downY, dx = e.getRawX() - downX;
                    if (canGrab() && dy > slop && dy > Math.abs(dx) * 1.1f) startTracking(e);
                    return true;
                }
                setTranslationY(Math.max(0f, Math.min(collapsedY, e.getRawY() - startRaw)));
                applyProgress();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (tracking) {
                    vtAdd(e);
                    float vy = 0f;
                    if (vt != null) {
                        vt.computeCurrentVelocity(1000);
                        vy = vt.getYVelocity();
                    }
                    tracking = false;
                    releaseVy = vy;
                    Ui.gestureEnd(this);
                    boolean closing = e.getActionMasked() == MotionEvent.ACTION_UP
                            && (vy > 900f || (vy > -900f && getTranslationY() > collapsedY * 0.28f));
                    if (closing) collapse(true);
                    else expand(true);
                }
                if (vt != null) {
                    vt.recycle();
                    vt = null;
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
            boolean drag, crossed;
            VelocityTracker t;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX();
                        y0 = e.getRawY();
                        drag = false;
                        crossed = false;
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
                            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);   // the cover is drawn once while it flies around
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        if (drag) {
                            float w = v.getWidth();
                            float shown = canGo(dx < 0) ? dx : dx * 0.28f;   // rubber band when there is nothing there
                            boolean over = canGo(dx < 0) && Math.abs(dx) > w * 0.28f;
                            if (over && !crossed) {          // a small tick when letting go would change the song
                                crossed = true;
                                Ui.tap(v);
                            } else if (!over && crossed) {
                                crossed = false;
                            }
                            v.setTranslationX(shown);
                            v.setRotation(shown / w * 10f);
                            v.setAlpha(1f - Math.min(0.55f, Math.abs(shown) / (w * 1.4f)));
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
                            if (up && Math.abs(dx) < slop && Math.abs(e.getRawY() - y0) < slop) onCoverTap(v, e.getX());
                            return true;
                        }
                        v.postDelayed(() -> v.setLayerType(View.LAYER_TYPE_NONE, null), 700);
                        float w = v.getWidth();
                        boolean go = up && (Math.abs(dx) > w * 0.28f || (Math.abs(vx) > 1100f && Math.signum(vx) == Math.signum(dx)));
                        boolean next = dx < 0;
                        MediaController mc = Pb.get();
                        if (go && !next && mc != null && mc.getCurrentPosition() > 3000) {
                            mc.seekTo(0);       // "previous" after 3 s only restarts the song, like every player
                            Ui.tap(v);
                            springBack(v);
                        } else if (go && canGo(next)) {
                            commitSwipe(v, next);
                        } else {
                            springBack(v);
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
    }

    private long lastTap, seekTime;
    private int lastZone = 2, seekZone = 2;
    private final android.os.Handler tapH = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingToggle;

    /**
     * Middle of the cover: play / pause at once. Left / right third: double tap seeks 10 s back / forward (keep tapping
     * to keep seeking); a single tap there plays / pauses after a short wait for the second tap.
     */
    private void onCoverTap(final View v, float x) {
        float rel = x / Math.max(1f, v.getWidth());
        int zone = rel < 0.32f ? -1 : rel > 0.68f ? 1 : 0;
        long now = android.os.SystemClock.uptimeMillis();
        if (pendingToggle != null) {
            tapH.removeCallbacks(pendingToggle);
            pendingToggle = null;
        }
        if (zone == 0) {
            lastZone = 2;
            togglePlay(v);
            return;
        }
        boolean chain = zone == seekZone && now - seekTime < 700;
        boolean dbl = zone == lastZone && now - lastTap < 320;
        if (chain || dbl) {
            Ui.tap(v);
            seekBy(zone * 10000L);
            SeekBubble.show(v, zone, zone > 0 ? "+10s" : "-10s");
            seekZone = zone;
            seekTime = now;
            lastZone = 2;
            return;
        }
        lastZone = zone;
        lastTap = now;
        pendingToggle = () -> {
            pendingToggle = null;
            togglePlay(v);
        };
        tapH.postDelayed(pendingToggle, 320);
    }

    private void seekBy(long delta) {
        MediaController m = Pb.get();
        if (m == null) return;
        long d = m.getDuration(), to = m.getCurrentPosition() + delta;
        if (d != androidx.media3.common.C.TIME_UNSET && d > 0) to = Math.min(to, d - 500);
        m.seekTo(Math.max(0, to));
    }

    private boolean canGo(boolean next) {
        MediaController m = Pb.get();
        if (m == null) return false;
        return next ? m.hasNextMediaItem() : (m.hasPreviousMediaItem() || m.getCurrentPosition() > 3000);
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
        Ui.confirm(v);
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
        tapH.removeCallbacksAndMessages(null);
        try {
            android.media.AudioManager am = (android.media.AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.unregisterAudioDeviceCallback(btWatch);
        } catch (Exception ignored) {
        }
        np.stop();
        super.onDetachedFromWindow();
    }
}
