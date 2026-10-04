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

/**
 * Lock-screen player, drawn above the keyguard: cover-colored backdrop, big clock, cover, glass panel with
 * title / seek / controls, swipe up to unlock. Opens from the full-screen-intent notification or from "Preview".
 */
public class LockActivity extends AppCompatActivity implements FullBleed {
    private NowPlaying np;
    private LinearLayout content;
    private ObjectAnimator pulse;
    private final BroadcastReceiver unlocked = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        Pb.connect(this);
        PlayerService.clearLock(this);

        int hgt = getResources().getConfiguration().screenHeightDp, w = getResources().getConfiguration().screenWidthDp;
        int art = Math.max(110, Math.min(300, Math.min(w - 90, hgt - 560)));
        np = new NowPlaying(this, art, true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.bg_default_backdrop);
        final ImageView bg = Backdrop.view(this);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackgroundResource(R.drawable.bg_scrim);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), Ui.dp(this, 8));

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

        // clock + date
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(64);
        time.setIncludeFontPadding(false);
        time.setTextColor(Ui.color(this, R.color.text_primary));
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setGravity(Gravity.CENTER);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(15);
        date.setTextColor(Ui.color(this, R.color.text_secondary));
        date.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = Ui.lp(-1, -2);
        tp.topMargin = Ui.dp(this, 18);
        content.addView(time, tp);
        content.addView(date, Ui.lp(-1, -2));

        content.addView(Ui.space(this, 1f));

        // cover
        content.addView(np.art);

        // glass panel
        LinearLayout glass = new LinearLayout(this);
        glass.setOrientation(LinearLayout.VERTICAL);
        glass.setBackgroundResource(R.drawable.bg_glass);
        glass.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 14));
        glass.addView(np.info, Ui.lp(-1, -2));
        LinearLayout.LayoutParams sp = Ui.lp(-1, -2);
        sp.topMargin = Ui.dp(this, 12);
        glass.addView(np.seekBlock, sp);
        glass.addView(np.controls, Ui.lp(-1, -2));
        LinearLayout.LayoutParams gp = Ui.lp(-1, -2);
        gp.topMargin = Ui.dp(this, 18);
        content.addView(glass, gp);

        content.addView(Ui.space(this, 0.45f));

        // swipe hint
        LinearLayout hint = new LinearLayout(this);
        hint.setOrientation(LinearLayout.VERTICAL);
        hint.setGravity(Gravity.CENTER);
        ImageView up = new ImageView(this);
        up.setImageResource(R.drawable.ic_arrow_down);
        up.setRotation(180f);
        Ui.tint(up, R.color.text_secondary);
        hint.addView(up, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView ht = Ui.text(this, getString(R.string.lock_hint), 13, R.color.text_secondary);
        ht.setGravity(Gravity.CENTER);
        hint.addView(ht, Ui.lp(-2, -2));
        content.addView(hint, Ui.lp(-1, -2));
        pulse = ObjectAnimator.ofFloat(hint, "translationY", 0f, -Ui.dp(this, 8));
        pulse.setDuration(950);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.start();

        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        Ui.edgeToEdge(this, content);

        np.artCb = u -> {
            if (Store.flag(this, "lock_blur", true)) Backdrop.set(this, bg, u);
            else Backdrop.set(this, bg, null);
        };
        setupSwipe(root);
        ContextCompat.registerReceiver(this, unlocked, new IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /** Drag upward; past the threshold the keyguard is dismissed (PIN / biometric if the device has one). */
    private void setupSwipe(View root) {
        final float[] y0 = {0};
        root.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    y0[0] = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = Math.min(0, e.getRawY() - y0[0]);
                    content.setTranslationY(dy * 0.7f);
                    content.setAlpha(Math.max(0.3f, 1f + dy / Ui.dp(this, 560)));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (e.getRawY() - y0[0] < -Ui.dp(this, 140)) unlock();
                    else reset();
                    return true;
                default:
                    return true;
            }
        });
    }

    private void reset() {
        content.animate().translationY(0).alpha(1f).setDuration(220).start();
    }

    private void unlock() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && km != null) {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    startActivity(new Intent(LockActivity.this, PlayerActivity.class));
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

    @Override
    protected void onDestroy() {
        if (pulse != null) pulse.cancel();
        try {
            unregisterReceiver(unlocked);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
