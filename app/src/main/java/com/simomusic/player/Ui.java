package com.simomusic.player;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.ImageViewCompat;

/** The same look as the GitHub Manager app: black canvas, graphite grouped cards, pill buttons, round icon buttons. */
public final class Ui {
    private Ui() {
    }

    /** Light tick on the key actions, like the system does. */
    /** startActivity that can never take the app down (missing app, background-start limits, odd ROMs). */
    public static void go(Context c, Intent i) {
        try {
            if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            CrashGuard.nonFatal("startActivity", e);
        }
    }

    public static void tap(View v) {
        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // One tactile vocabulary for the whole app. Android 11+ uses the system's own effects, so the phone's
    // haptic motor (One UI "System vibration" intensity) decides how strong they feel, like in Samsung's apps.

    /** An action went through (favorite on, song changed by a swipe). */
    public static void confirm(View v) {
        v.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                ? android.view.HapticFeedbackConstants.CONFIRM : android.view.HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** A drag has started / has been let go (player sheet, lock-screen swipe). */
    public static void gestureStart(View v) {
        v.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                ? android.view.HapticFeedbackConstants.GESTURE_START : android.view.HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public static void gestureEnd(View v) {
        v.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                ? android.view.HapticFeedbackConstants.GESTURE_END : android.view.HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** A very light tick while a finger moves over a scale (the seek bar). */
    public static void tick(View v) {
        v.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 34
                ? android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK : android.view.HapticFeedbackConstants.CLOCK_TICK);
    }

    /**
     * Titles use the phone's own system font (One UI Sans, or whatever font the user picked in Settings > Font style),
     * so the app reads like part of One UI instead of carrying its own typeface.
     */
    public static Typeface titleFace(boolean bold) {
        if (android.os.Build.VERSION.SDK_INT >= 28) return Typeface.create(Typeface.DEFAULT, bold ? 700 : 400, false);
        return Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
    }

    public static boolean isNight(Context c) {
        return (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Status bar and navigation bar icons. Dark screens (the player and the lock screen sit on cover art, always dark)
     * need light icons, light screens need dark ones; with the wrong pair the clock and battery disappear.
     * The status bar is also explicitly shown, so a screen can never leave it hidden.
     */
    public static void bars(android.app.Activity a, boolean darkScreen) {
        try {
            android.view.Window w = a.getWindow();
            androidx.core.view.WindowInsetsControllerCompat ic = androidx.core.view.WindowCompat.getInsetsController(w, w.getDecorView());
            ic.setAppearanceLightStatusBars(!darkScreen);
            ic.setAppearanceLightNavigationBars(!darkScreen);
            ic.show(androidx.core.view.WindowInsetsCompat.Type.statusBars());
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("system bars", e);
        }
    }

    public static View.OnClickListener haptic(final View.OnClickListener l) {
        return v -> {
            tap(v);
            l.onClick(v);
        };
    }

    /** A context that always resolves the dark (AMOLED) colors: the full player and lock screen sit on cover art. */
    public static Context dark(Context c) {
        android.content.res.Configuration cfg = new android.content.res.Configuration(c.getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                | android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.appcompat.view.ContextThemeWrapper w =
                new androidx.appcompat.view.ContextThemeWrapper(c.createConfigurationContext(cfg), R.style.AppTheme);
        Accent.apply(w.getTheme(), c);
        return w;
    }

    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int color(Context c, int res) {
        int attr = accentAttr(res);
        if (attr != 0) {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (c.getTheme().resolveAttribute(attr, tv, true) && tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                    && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) return tv.data;
        }
        return ContextCompat.getColor(c, res);
    }

    /** The accent-related colors follow the chosen accent; everything else is a plain resource. */
    private static int accentAttr(int res) {
        if (res == R.color.accent || res == R.color.pill_primary) return R.attr.nAccent;
        if (res == R.color.accent_text) return R.attr.nAccentText;
        if (res == R.color.accent_soft) return R.attr.nAccentSoft;
        if (res == R.color.on_accent) return R.attr.nOnAccent;
        return 0;
    }

    /** Soft accent glow fading out below the top of the screen; used as the window background of main screens. */
    public static android.graphics.drawable.Drawable glow(Context c) {
        android.util.TypedValue tv = new android.util.TypedValue();
        int g = 0x334477FF;
        if (c.getTheme().resolveAttribute(R.attr.nAccentGlow, tv, true)) g = tv.data;
        // the system (wallpaper) accent hands over an opaque color: give it the same soft strength as the fixed ones
        if ((g >>> 24) == 0xFF) g = (g & 0x00FFFFFF) | (isNight(c) ? 0x4A000000 : 0x24000000);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{g, g & 0x00FFFFFF});
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{new android.graphics.drawable.ColorDrawable(color(c, R.color.bg)), gd});
        ld.setLayerGravity(1, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        ld.setLayerHeight(1, dp(c, 360));
        return ld;
    }

    private static android.animation.StateListAnimator pressProto;

    /** The press-scale effect is parsed from XML once and cloned: building a row used to re-parse it for every button. */
    public static void press(Context c, View v) {
        if (pressProto == null) {
            try {
                pressProto = AnimatorInflater.loadStateListAnimator(c.getApplicationContext(), R.animator.press_scale);
            } catch (RuntimeException e) {
                v.setStateListAnimator(AnimatorInflater.loadStateListAnimator(c, R.animator.press_scale));
                return;
            }
        }
        v.setStateListAnimator(pressProto.clone());
    }

    /**
     * Asks for the display's highest refresh rate (120 Hz on Galaxy S / Fold / Flip) while the app is on screen. Without
     * it One UI's adaptive refresh drops animations that are not driven by a finger (sheet settling, fades) to 60 Hz or
     * lower, which is the "slightly laggy" feel even when nothing is slow.
     */
    public static void highRefresh(android.app.Activity a) {
        try {
            android.view.Display d = a.getWindowManager().getDefaultDisplay();
            android.view.Display.Mode cur = d.getMode(), best = cur;
            for (android.view.Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate() + 0.5f) best = m;
            }
            if (best.getModeId() != cur.getModeId()) {
                android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
                lp.preferredDisplayModeId = best.getModeId();
                a.getWindow().setAttributes(lp);
            }
        } catch (RuntimeException ignored) {
        }
    }

    public static void round(View v, final int radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    public static void tint(ImageView v, int colorRes) {
        ImageViewCompat.setImageTintList(v, ColorStateList.valueOf(color(v.getContext(), colorRes)));
    }

    public static TextView text(Context c, CharSequence t, int sp, int colorRes) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color(c, colorRes));
        return v;
    }

    /** Draws the screen under the system bars and pads the given view by their insets (+ optional extra). */
    public static void edgeToEdge(android.app.Activity a, View content) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(a.getWindow(), false);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = android.os.Build.VERSION.SDK_INT >= 30
                    ? android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            a.getWindow().setAttributes(lp);
        }
        a.getWindow().setStatusBarColor(0);
        a.getWindow().setNavigationBarColor(0);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            a.getWindow().setNavigationBarContrastEnforced(false);
            a.getWindow().setStatusBarContrastEnforced(false);   // One UI must not add its own scrim behind our top bar
        }
        bars(a, isNight(a));
        final int l = content.getPaddingLeft(), t = content.getPaddingTop(), r = content.getPaddingRight(), b = content.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            androidx.core.graphics.Insets ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(content);
    }

    /** Fade + slide up, used to stagger screen content in. */
    public static void enter(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 22));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
    }

    /** Small springy pop (play/pause, heart, new cover). */
    public static void pop(View v) {
        v.setScaleX(0.82f);
        v.setScaleY(0.82f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
    }

    /** Custom open/close transitions that also work with predictive back on Android 14+. */
    public static void transitions(android.app.Activity a, int openIn, int openOut, int closeIn, int closeOut) {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            a.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, openIn, openOut);
            a.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, closeIn, closeOut);
        } else {
            a.overridePendingTransition(openIn, openOut);
        }
    }

    public static void legacyClose(android.app.Activity a, int closeIn, int closeOut) {
        if (android.os.Build.VERSION.SDK_INT < 34) a.overridePendingTransition(closeIn, closeOut);
    }

    public static android.widget.Space space(Context c, float weight) {
        android.widget.Space s = new android.widget.Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, 0, weight));
        return s;
    }

    /** Two or more pills sharing the row width equally. */
    public static LinearLayout pillRow(Context c, View... pills) {
        LinearLayout row = new LinearLayout(c);
        row.setPadding(dp(c, 20), dp(c, 6), dp(c, 20), dp(c, 10));
        for (int i = 0; i < pills.length; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) p.setMarginStart(dp(c, 10));
            row.addView(pills[i], p);
        }
        return row;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    /** Round graphite icon button (the header buttons of the GitHub Manager app). */
    public static ImageButton icon(Context c, int res, int desc, View.OnClickListener l) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(res);
        b.setBackgroundResource(R.drawable.bg_icon_btn);
        b.setScaleType(ImageView.ScaleType.CENTER);
        tint(b, R.color.text_primary);
        b.setLayoutParams(lp(dp(c, 48), dp(c, 48)));
        if (desc != 0) b.setContentDescription(c.getString(desc));
        if (l != null) b.setOnClickListener(haptic(l));
        press(c, b);
        return b;
    }

    /** Borderless transport / action button. */
    public static ImageButton flat(Context c, int res, int sizeDp, int desc, View.OnClickListener l) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(res);
        b.setBackgroundResource(R.drawable.bg_ripple_round);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = dp(c, sizeDp / 4);
        b.setPadding(p, p, p, p);
        tint(b, R.color.text_primary);
        b.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        if (desc != 0) b.setContentDescription(c.getString(desc));
        if (l != null) b.setOnClickListener(haptic(l));
        press(c, b);
        return b;
    }

    /** The big white play / pause circle. */
    public static ImageButton bigPlay(Context c, View.OnClickListener l) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(R.drawable.ic_play_fill);
        b.setBackgroundResource(R.drawable.bg_play_circle);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = dp(c, 22);
        b.setPadding(p, p, p, p);
        tint(b, R.color.on_accent);
        b.setLayoutParams(lp(dp(c, 72), dp(c, 72)));
        b.setContentDescription(c.getString(R.string.play_pause));
        b.setOnClickListener(haptic(l));
        press(c, b);
        return b;
    }

    public static ImageView artView(Context c, int sizeDp, int radiusDp) {
        ImageView iv = new ImageView(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(c, R.color.surface_high));
        g.setCornerRadius(dp(c, radiusDp));
        iv.setBackground(g);
        round(iv, dp(c, radiusDp));
        iv.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    public static Button pill(Context c, int textRes, int iconRes, boolean primary, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(textRes);
        b.setAllCaps(false);
        b.setBackgroundResource(primary ? R.drawable.btn_primary : R.drawable.btn_secondary);
        int col = color(c, primary ? R.color.on_accent : R.color.text_primary);
        b.setTextColor(col);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(c, 46));
        b.setMinimumHeight(dp(c, 46));
        b.setPaddingRelative(dp(c, 18), 0, dp(c, 20), 0);
        if (iconRes != 0) {
            Drawable d = DrawableCompat.wrap(ContextCompat.getDrawable(c, iconRes).mutate());
            DrawableCompat.setTint(d, col);
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
            b.setCompoundDrawablePadding(dp(c, 8));
        }
        b.setOnClickListener(haptic(l));
        press(c, b);
        return b;
    }

    public static EditText edit(Context c, CharSequence hint, CharSequence text) {
        EditText e = new EditText(c);
        e.setHint(hint);
        if (text != null) e.setText(text);
        e.setSingleLine(true);
        e.setBackgroundResource(R.drawable.bg_input);
        e.setTextColor(color(c, R.color.text_primary));
        e.setHintTextColor(color(c, R.color.text_hint));
        e.setTextSize(15);
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        e.setMinHeight(dp(c, 52));
        e.setPaddingRelative(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        LinearLayout.LayoutParams lp = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        e.setLayoutParams(lp);
        return e;
    }

    public static TextView chip(Context c, CharSequence text, boolean selected) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(13);
        t.setSingleLine(true);
        t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        LinearLayout.LayoutParams lp = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(c, 8));
        t.setLayoutParams(lp);
        setChip(c, t, selected);
        press(c, t);
        return t;
    }

    public static void setChip(Context c, TextView t, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 100));
        g.setStroke(dp(c, 1), color(c, selected ? R.color.accent : R.color.stroke));
        g.setColor(color(c, selected ? R.color.accent_soft : R.color.surface));
        t.setBackground(g);
        t.setTextColor(color(c, selected ? R.color.accent_text : R.color.text_secondary));
    }

    public static TextView section(Context c, int textRes) {
        TextView t = text(c, c.getString(textRes), 14, R.color.accent_text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.04f);
        t.setPaddingRelative(dp(c, 26), dp(c, 22), dp(c, 26), dp(c, 8));
        return t;
    }

    /** Top bar: round back button, centered serif title, optional round action buttons. */
    public static LinearLayout topBar(final android.app.Activity a, CharSequence title, int backIcon, ImageButton... actions) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 8));
        bar.addView(icon(a, backIcon, R.string.back, v -> a.finish()));
        TextView t = text(a, title, 20, R.color.text_primary);
        t.setTypeface(Ui.titleFace(false));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = weight(1);
        lp.setMargins(dp(a, 8), 0, dp(a, 8), 0);
        bar.addView(t, lp);
        if (actions.length == 0) {
            View sp = new View(a);
            bar.addView(sp, lp(dp(a, 48), dp(a, 48)));
        }
        for (ImageButton b : actions) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) b.getLayoutParams();
            p.setMarginStart(dp(a, 6));
            bar.addView(b);
        }
        return bar;
    }

    // ------------------------------------------------------------------ grouped rows

    /** Rounds a card like the ChatGPT-style grouped lists: big corners at the group ends, small in between. */
    private static final java.util.HashMap<String, android.graphics.drawable.Drawable.ConstantState> SHAPES = new java.util.HashMap<>();

    public static void shape(Context c, View card, boolean first, boolean last, int fillRes) {
        int fillCol = color(c, fillRes), ripCol = color(c, R.color.ripple), bigPx = dp(c, 24);
        String key = first + "," + last + "," + fillCol + "," + ripCol + "," + bigPx;
        android.graphics.drawable.Drawable.ConstantState cs = SHAPES.get(key);
        if (cs != null) {
            card.setBackground(cs.newDrawable(c.getResources()));
            return;
        }
        float big = bigPx, small = dp(c, 6);
        float top = first ? big : small, bottom = last ? big : small;
        float[] r = {top, top, top, top, bottom, bottom, bottom, bottom};
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(fillCol);
        fill.setCornerRadii(r);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadii(r);
        RippleDrawable rd = new RippleDrawable(ColorStateList.valueOf(ripCol), fill, mask);
        android.graphics.drawable.Drawable.ConstantState st = rd.getConstantState();
        if (st != null) {
            if (SHAPES.size() > 64) SHAPES.clear();
            SHAPES.put(key, st);
        }
        card.setBackground(rd);
    }

    /**
     * Drag a sheet down by its header: follows the finger, rubber-bands upward, and closes on a fling or past
     * a short distance (it animates out first, so there is no jump). Can be caught again mid-spring.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    public static void dragDismiss(final View head, final View root, final android.app.Dialog d) {
        final Context c = head.getContext();
        final int slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop();
        final float[] st = new float[2]; // [0] raw y at down, [1] translation at down
        final android.view.VelocityTracker[] vt = new android.view.VelocityTracker[1];
        final boolean[] moving = {false};
        head.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    root.animate().cancel();
                    st[0] = e.getRawY();
                    st[1] = root.getTranslationY();
                    moving[0] = st[1] != 0f;
                    if (vt[0] != null) vt[0].recycle();
                    vt[0] = android.view.VelocityTracker.obtain();
                    vt[0].addMovement(e);
                    return true;
                case android.view.MotionEvent.ACTION_MOVE: {
                    if (vt[0] == null) return false;
                    vt[0].addMovement(e);
                    float dy = e.getRawY() - st[0];
                    if (!moving[0] && Math.abs(dy) < slop) return true;
                    moving[0] = true;
                    float t = st[1] + dy;
                    root.setTranslationY(t >= 0f ? t : t * 0.12f);
                    return true;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: {
                    if (vt[0] == null) return false;
                    vt[0].addMovement(e);
                    vt[0].computeCurrentVelocity(1000);
                    float vy = vt[0].getYVelocity();
                    vt[0].recycle();
                    vt[0] = null;
                    boolean up = e.getActionMasked() == android.view.MotionEvent.ACTION_UP;
                    float ty = root.getTranslationY();
                    if (up && (vy > 1100f || (vy > -500f && ty > dp(c, 110)))) {
                        root.animate().translationY(Math.max(root.getHeight(), dp(c, 200))).setDuration(200)
                                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f))
                                .withEndAction(() -> {
                                    try {
                                        d.dismiss();
                                    } catch (Exception ignored) {
                                    }
                                }).start();
                    } else {
                        root.animate().translationY(0f).setDuration(280)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator(2.2f)).start();
                    }
                    return true;
                }
                default:
                    return false;
            }
        });
    }

    /** Shapes every consecutive run of row cards inside a container as one rounded group. */
    public static void group(Context c, ViewGroup g) {
        int n = g.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = g.getChildAt(i);
            if (v.findViewById(R.id.card) == null) continue;
            boolean first = i == 0 || g.getChildAt(i - 1).findViewById(R.id.card) == null;
            boolean last = i == n - 1 || g.getChildAt(i + 1).findViewById(R.id.card) == null;
            shape(c, v.findViewById(R.id.card), first, last, R.color.surface);
        }
    }

    private static FrameLayout rowShell(Context c, LinearLayout card) {
        FrameLayout w = new FrameLayout(c);
        w.setPadding(dp(c, 14), dp(c, 1), dp(c, 14), dp(c, 1));
        card.setId(R.id.card);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(c, 64));
        card.setPaddingRelative(dp(c, 18), dp(c, 12), dp(c, 16), dp(c, 12));
        card.setDuplicateParentStateEnabled(true);
        w.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return w;
    }

    private static LinearLayout textColumn(Context c, CharSequence title, CharSequence sub) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(c, title, 16, R.color.text_primary));
        if (sub != null) {
            TextView s = text(c, sub, 13, R.color.text_secondary);
            s.setPadding(0, dp(c, 3), 0, 0);
            col.addView(s);
        }
        return col;
    }

    /** A settings row; trailing is an optional pill (e.g. "Enabled"), icon an optional leading icon. */
    public static View settingRow(Context c, int iconRes, CharSequence title, CharSequence sub,
                                  CharSequence trailing, boolean trailingOk, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(c);
        FrameLayout w = rowShell(c, card);
        if (iconRes != 0) {
            ImageView i = new ImageView(c);
            i.setImageResource(iconRes);
            tint(i, R.color.text_secondary);
            card.addView(i, lp(dp(c, 24), dp(c, 24)));
        }
        LinearLayout.LayoutParams lp = weight(1);
        lp.setMarginStart(dp(c, iconRes != 0 ? 16 : 0));
        lp.setMarginEnd(dp(c, 10));
        card.addView(textColumn(c, title, sub), lp);
        if (trailing != null) {
            TextView b = text(c, trailing, 12, trailingOk ? R.color.ok : R.color.accent_text);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setPadding(dp(c, 12), dp(c, 5), dp(c, 12), dp(c, 5));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(c, 100));
            g.setColor(color(c, trailingOk ? R.color.neutral_soft : R.color.accent_soft));
            b.setBackground(g);
            card.addView(b);
        } else if (click != null) {
            ImageView ch = new ImageView(c);
            ch.setImageResource(R.drawable.ic_chevron);
            tint(ch, R.color.text_hint);
            card.addView(ch, lp(dp(c, 20), dp(c, 20)));
        }
        if (click != null) w.setOnClickListener(click);
        return w;
    }

    public static View toggleRow(Context c, CharSequence title, CharSequence sub, boolean on,
                                 final CompoundButton.OnCheckedChangeListener l) {
        LinearLayout card = new LinearLayout(c);
        FrameLayout w = rowShell(c, card);
        LinearLayout.LayoutParams lp = weight(1);
        lp.setMarginEnd(dp(c, 14));
        card.addView(textColumn(c, title, sub), lp);
        final SwitchCompat sw = new SwitchCompat(c);
        sw.setBackground(null);
        sw.setShowText(false);
        sw.setThumbDrawable(ContextCompat.getDrawable(c, R.drawable.switch_thumb));
        sw.setTrackDrawable(ContextCompat.getDrawable(c, R.drawable.switch_track));
        sw.setChecked(on);
        sw.setClickable(false);
        sw.setFocusable(false);
        card.addView(sw);
        w.setOnClickListener(v -> {
            tap(v);
            sw.toggle();
            l.onCheckedChanged(sw, sw.isChecked());
            androidx.core.view.ViewCompat.setStateDescription(w, c.getString(sw.isChecked() ? R.string.perm_on : R.string.state_off));
        });
        androidx.core.view.ViewCompat.setStateDescription(w, c.getString(on ? R.string.perm_on : R.string.state_off));
        androidx.core.view.ViewCompat.setAccessibilityDelegate(w, new androidx.core.view.AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Switch");
                info.setCheckable(true);
                info.setChecked(sw.isChecked());
            }
        });
        return w;
    }

    // ------------------------------------------------------------------ polish helpers

    public static void toast(Context c, int res) {
        toast(c, c.getString(res));
    }

    /** Small pill that slides up above the mini player, replaces the stock Toast. */
    public static void toast(final Context c, CharSequence msg) {
        ViewGroup content = c instanceof android.app.Activity ? ((android.app.Activity) c).findViewById(android.R.id.content) : null;
        if (content == null) {
            android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Object old = content.getTag(R.id.tag_toast);
        if (old instanceof View) content.removeView((View) old);
        final TextView t = text(c, msg, 14, R.color.text_primary);
        t.setBackgroundResource(R.drawable.bg_toast);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setPadding(dp(c, 22), dp(c, 13), dp(c, 22), dp(c, 13));
        t.setElevation(dp(c, 8));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(c, 96);
        lp.leftMargin = lp.rightMargin = dp(c, 24);
        content.addView(t, lp);
        content.setTag(R.id.tag_toast, t);
        t.setAlpha(0f);
        t.setTranslationY(dp(c, 14));
        t.animate().alpha(1f).translationY(0f).setDuration(240)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                .withEndAction(() -> t.postDelayed(() -> {
                    if (t.getParent() != null) t.animate().alpha(0f).translationY(dp(c, 10)).setDuration(220)
                            .withEndAction(() -> {
                                if (t.getParent() instanceof ViewGroup) ((ViewGroup) t.getParent()).removeView(t);
                            }).start();
                }, 1700)).start();
    }

    /** Search field with a leading magnifier. */
    public static EditText searchEdit(Context c, CharSequence hint) {
        EditText e = edit(c, hint, null);
        Drawable d = DrawableCompat.wrap(ContextCompat.getDrawable(c, R.drawable.ic_search).mutate());
        DrawableCompat.setTint(d, color(c, R.color.text_hint));
        d.setBounds(0, 0, dp(c, 20), dp(c, 20));
        e.setCompoundDrawablesRelative(d, null, null, null);
        e.setCompoundDrawablePadding(dp(c, 12));
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        return e;
    }

    /** Loading placeholder that mirrors the real list (two pills + rows), so the screen never jumps. */
    public static LinearLayout skeleton(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pills = new LinearLayout(c);
        pills.setPadding(dp(c, 20), dp(c, 6), dp(c, 20), dp(c, 10));
        for (int i = 0; i < 2; i++) {
            View p = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(c, 100));
            g.setColor(color(c, R.color.surface));
            p.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(c, 46), 1f);
            if (i > 0) lp.setMarginStart(dp(c, 10));
            pills.addView(p, lp);
        }
        col.addView(pills);
        int[] w1 = {170, 210, 140, 190, 160, 220, 150, 200};
        for (int i = 0; i < w1.length; i++) {
            FrameLayout shell = new FrameLayout(c);
            shell.setPadding(dp(c, 14), dp(c, 1), dp(c, 14), dp(c, 1));
            LinearLayout card = new LinearLayout(c);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPaddingRelative(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
            shape(c, card, i == 0, i == w1.length - 1, R.color.surface);
            card.addView(block(c, 52, 52, 14));
            LinearLayout t = new LinearLayout(c);
            t.setOrientation(LinearLayout.VERTICAL);
            t.addView(block(c, w1[i], 14, 7));
            LinearLayout.LayoutParams sp = lp(dp(c, w1[i] - 60), dp(c, 11));
            sp.topMargin = dp(c, 9);
            t.addView(block(c, w1[i] - 60, 11, 6), sp);
            LinearLayout.LayoutParams tp = lp(-2, -2);
            tp.setMarginStart(dp(c, 14));
            card.addView(t, tp);
            shell.addView(card, new FrameLayout.LayoutParams(-1, -2));
            col.addView(shell, new LinearLayout.LayoutParams(-1, -2));
        }
        return col;
    }

    private static View block(Context c, int wDp, int hDp, int rDp) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, rDp));
        g.setColor(color(c, R.color.surface_high));
        v.setBackground(g);
        v.setLayoutParams(lp(dp(c, wDp), dp(c, hDp)));
        return v;
    }

    public interface ChipToggle {
        /** Return false to refuse the change (the chip stays as it was). */
        boolean onToggle(int index, boolean wantOn, int enabledCount);
    }

    /** Compact on/off pills in rows of three inside one rounded card (used for the audio formats). */
    public static View chipGrid(final Context c, final String[] names, final String[] desc, final boolean[] on, final ChipToggle cb) {
        FrameLayout shell = new FrameLayout(c);
        shell.setPadding(dp(c, 14), dp(c, 1), dp(c, 14), dp(c, 1));
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        shape(c, card, true, true, R.color.surface);
        card.setBackground(((RippleDrawable) card.getBackground()).getDrawable(0));
        final TextView[] chips = new TextView[names.length];
        LinearLayout row = null;
        for (int i = 0; i < names.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(c);
                card.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            final int idx = i;
            final TextView t = new TextView(c);
            t.setText(names[i]);
            t.setTextSize(14);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setContentDescription(names[i] + ", " + desc[i]);
            chips[i] = t;
            styleChip(c, t, on[i]);
            t.setOnClickListener(v -> {
                int cnt = 0;
                for (boolean b : on) if (b) cnt++;
                boolean want = !on[idx];
                if (cb.onToggle(idx, want, cnt)) {
                    on[idx] = want;
                    styleChip(c, t, want);
                    tap(v);
                } else {
                    v.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                            ? android.view.HapticFeedbackConstants.REJECT : android.view.HapticFeedbackConstants.LONG_PRESS);
                }
            });
            press(c, t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(c, 44), 1f);
            lp.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
            row.addView(t, lp);
        }
        shell.addView(card, new FrameLayout.LayoutParams(-1, -2));
        return shell;
    }

    private static void styleChip(Context c, TextView t, boolean on) {
        t.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_off);
        t.setTextColor(color(c, on ? R.color.on_accent : R.color.text_secondary));
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }
}
