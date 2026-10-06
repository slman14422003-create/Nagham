package com.nagham.player;

import android.animation.ObjectAnimator;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * Lock-screen player, drawn above the keyguard on Pixel, Samsung (One UI) and Xiaomi (MIUI / HyperOS):
 * show-when-locked + turn-screen-on, cutout-aware edge-to-edge window, adaptive layout, swipe up to unlock.
 * It closes itself when the screen turns off or the device is unlocked; the service re-opens it on the next wake.
 */
public class LockActivity extends AppCompatActivity implements FullBleed {
    private NowPlaying np;
    private LinearLayout content;
    private ObjectAnimator pulse;
    private final BroadcastReceiver closer = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            LockLauncher.lastSeen = android.os.SystemClock.elapsedRealtime();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(b);
        // phones stay portrait (the manifest says so); tablets and foldables may rotate
        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        if (Build.VERSION.SDK_INT >= 28) { // punch-hole / notch: draw into the cutout, insets keep content clear
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        Ui.transitions(this, R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out);
        Pb.connect(this);
        LockLauncher.clear(this);
        np = new NowPlaying(this, 300, true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg_default_backdrop);
        final ImageView bg = Backdrop.view(this);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        ArtGlow glow = new ArtGlow(this);
        root.addView(glow, new FrameLayout.LayoutParams(-1, -1));
        np.onTint = glow::setColor;
        View scrim = new View(this);
        scrim.setBackgroundResource(R.drawable.bg_scrim);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), Ui.dp(this, 6));

        // top: app chip + close
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_pill_glass);
        chip.setPaddingRelative(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 16), Ui.dp(this, 7));
        ImageView ci = new ImageView(this);
        ci.setImageResource(R.drawable.ic_music);
        Ui.tint(ci, R.color.text_primary);
        chip.addView(ci, Ui.lp(Ui.dp(this, 18), Ui.dp(this, 18)));
        TextView cn = Ui.text(this, getString(R.string.app_name), 13, R.color.text_primary);
        cn.setTypeface(Typeface.DEFAULT_BOLD);
        cn.setPaddingRelative(Ui.dp(this, 8), 0, 0, 0);
        chip.addView(cn);
        top.addView(chip);
        top.addView(Ui.space(this, 1f), new LinearLayout.LayoutParams(1, 1, 1f));
        top.addView(Ui.icon(this, R.drawable.ic_close, R.string.close, v -> finish()));
        content.addView(top, Ui.lp(-1, -2));

        // clock + date (localized pattern, follows 12/24h)
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(60);
        time.setIncludeFontPadding(false);
        time.setTextColor(Ui.color(this, R.color.text_primary));
        // the phone's own system font (One UI Sans, MiSans, Google Sans ...) in a light weight, like its own lock screen
        time.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(Typeface.DEFAULT, 300, false)
                : Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setGravity(Gravity.CENTER);
        TextClock date = new TextClock(this);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        date.setTextSize(15);
        date.setTextColor(Ui.color(this, R.color.text_secondary));
        date.setGravity(Gravity.CENTER);
        clockBox.addView(time, Ui.lp(-1, -2));
        clockBox.addView(date, Ui.lp(-1, -2));
        status = Ui.text(this, "", 13, R.color.text_secondary);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(this, 4), 0, 0);
        status.setVisibility(View.GONE);
        clockBox.addView(status, Ui.lp(-1, -2));
        LinearLayout.LayoutParams clp = Ui.lp(-1, -2);
        clp.topMargin = Ui.dp(this, 14);
        content.addView(clockBox, clp);

        // cover: biggest square that fits between the clock and the panel
        CoverBox cover = new CoverBox(this, 300);
        cover.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        cover.setCover(np.art);
        content.addView(cover, new LinearLayout.LayoutParams(-1, 0, 1f));

        // glass panel
        LinearLayout glass = new LinearLayout(this);
        glass.setOrientation(LinearLayout.VERTICAL);
        glass.setBackgroundResource(R.drawable.bg_glass);
        glass.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 14));
        glass.addView(np.info, Ui.lp(-1, -2));
        LinearLayout.LayoutParams sp = Ui.lp(-1, -2);
        sp.topMargin = Ui.dp(this, 10);
        glass.addView(np.seekBlock, sp);
        glass.addView(np.controls, Ui.lp(-1, -2));
        content.addView(glass, Ui.lp(-1, -2));

        // swipe hint
        LinearLayout hint = new LinearLayout(this);
        hint.setOrientation(LinearLayout.VERTICAL);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        ImageView up = new ImageView(this);
        up.setImageResource(R.drawable.ic_arrow_down);
        up.setRotation(180f);
        Ui.tint(up, R.color.text_secondary);
        hint.addView(up, Ui.lp(Ui.dp(this, 22), Ui.dp(this, 22)));
        TextView ht = Ui.text(this, getString(R.string.lock_hint), 13, R.color.text_secondary);
        ht.setGravity(Gravity.CENTER);
        hint.addView(ht, Ui.lp(-2, -2));
        content.addView(hint, Ui.lp(-1, -2));
        pulse = ObjectAnimator.ofFloat(up, "translationY", 0f, -Ui.dp(this, 7));
        pulse.setDuration(900);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        if (animationsOn()) pulse.start();

        // same column on a phone, a centered column (max 520 dp) on tablets and unfolded foldables
        root.addView(content, new FrameLayout.LayoutParams(
                Math.min(getResources().getDisplayMetrics().widthPixels, Ui.dp(this, 520)), -1, Gravity.CENTER_HORIZONTAL));
        setContentView(root);
        Ui.edgeToEdge(this, content);

        np.artCb = u -> Backdrop.set(this, bg, Store.flag(this, "lock_blur", true) ? u : null);
        setupSwipe(root);
        IntentFilter f = new IntentFilter(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        ContextCompat.registerReceiver(this, closer, f, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (animationsOn()) {
            Ui.enter(clockBox, 0);
            Ui.enter(cover, 90);
            Ui.enter(glass, 170);
            Ui.enter(hint, 260);
        }
    }

    private TextView status;
    private final android.os.Handler clockH = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            clockH.postDelayed(this, 60000);
        }
    };

    /** Respect the system "remove animations" setting. */
    private static boolean animationsOn() {
        return Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled();
    }

    /** Next alarm and charging / low battery, the way the system lock screen shows them. */
    private void updateStatus() {
        try {
            StringBuilder sb = new StringBuilder();
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            android.app.AlarmManager.AlarmClockInfo ai = am == null ? null : am.getNextAlarmClock();
            if (ai != null) {
                String p = DateFormat.getBestDateTimePattern(Locale.getDefault(), DateFormat.is24HourFormat(this) ? "EEEHm" : "EEEhm");
                sb.append(getString(R.string.lock_alarm, new java.text.SimpleDateFormat(p, Locale.getDefault()).format(new java.util.Date(ai.getTriggerTime()))));
            }
            Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bi != null) {
                int lvl = bi.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1), scale = bi.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
                int st = bi.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                boolean charging = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING || st == android.os.BatteryManager.BATTERY_STATUS_FULL
                        || bi.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;
                int pct = lvl < 0 ? -1 : Math.round(lvl * 100f / Math.max(1, scale));
                if (pct >= 0 && (charging || pct <= 20)) {
                    if (sb.length() > 0) sb.append("   \u00B7   ");
                    sb.append(getString(charging ? R.string.lock_charging : R.string.lock_battery, pct));
                }
            }
            status.setText(sb);
            status.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("lock status", e);
        }
    }

    /** Drag upward; past the threshold the keyguard is dismissed (PIN / pattern / fingerprint if one is set). */
    private void setupSwipe(View root) {
        final float[] y0 = {0};
        final android.view.VelocityTracker[] vt = new android.view.VelocityTracker[1];
        root.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    y0[0] = e.getRawY();
                    if (vt[0] != null) vt[0].recycle();
                    vt[0] = android.view.VelocityTracker.obtain();
                    vt[0].addMovement(e);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (vt[0] == null) return true;
                    vt[0].addMovement(e);
                    float dy = Math.min(0, e.getRawY() - y0[0]);
                    content.setTranslationY(dy * 0.7f);
                    content.setAlpha(Math.max(0.3f, 1f + dy / Ui.dp(this, 560)));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float vy = 0f;
                    if (vt[0] != null) {
                        vt[0].addMovement(e);
                        vt[0].computeCurrentVelocity(1000);
                        vy = vt[0].getYVelocity();
                        vt[0].recycle();
                        vt[0] = null;
                    }
                    boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                    float dist = e.getRawY() - y0[0];
                    if (up && (dist < -Ui.dp(this, 140) || (vy < -1400f && dist < -Ui.dp(this, 40)))) unlock();
                    else reset();
                    return true;
                }
                default:
                    return true;
            }
        });
    }

    private void reset() {
        content.animate().translationY(0).alpha(1f).setDuration(240)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
    }

    private void unlock() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && km != null) {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    Ui.go(LockActivity.this, new Intent(LockActivity.this, PlayerActivity.class));
                    finish();
                }

                @Override
                public void onDismissCancelled() {
                    reset();
                }

                @Override
                public void onDismissError() {
                    reset();
                }
            });
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            finish();
        }
    }

    @Override
    public void finish() {
        super.finish();
        Ui.legacyClose(this, R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Pb.connect(this);
        LockLauncher.lastSeen = android.os.SystemClock.elapsedRealtime();
        np.start();
        clockH.post(statusTick);
    }

    @Override
    protected void onStop() {
        LockLauncher.lastSeen = android.os.SystemClock.elapsedRealtime();
        np.stop();
        clockH.removeCallbacksAndMessages(null);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (pulse != null) pulse.cancel();
        try {
            unregisterReceiver(closer);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
