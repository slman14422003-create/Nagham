package com.nagham.player;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Lock-screen player: shown over the keyguard (Android 8.1+ API, with the legacy flags below that), blurred cover
 * backdrop, big clock, controls, swipe up to unlock. Opens through the full-screen-intent notification or the
 * "lock screen preview" button.
 */
public class LockActivity extends AppCompatActivity implements FullBleed {
    private NowPlaying np;
    private ImageView bg;
    private LinearLayout content;
    private final BroadcastReceiver unlocked = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        Pb.connect(this);
        PlayerService.clearLock(this);

        int w = getResources().getConfiguration().screenWidthDp, hgt = getResources().getConfiguration().screenHeightDp;
        int art = Math.max(120, Math.min(300, Math.min((int) (w * 0.78f), (int) (hgt * 0.32f))));
        np = new NowPlaying(this, art);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setScaleX(1.3f);
        bg.setScaleY(1.3f);
        if (Build.VERSION.SDK_INT >= 31 && Store.flag(this, "lock_blur", true)) {
            bg.setRenderEffect(RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP));
        }
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0xCC000000, 0xF2000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(Ui.dp(this, 28), 0, Ui.dp(this, 28), 0);

        ImageButton close = Ui.icon(this, R.drawable.ic_close, R.string.close, v -> finish());
        LinearLayout top = new LinearLayout(this);
        top.addView(new View(this), Ui.weight(1));
        top.addView(close);
        content.addView(top, Ui.lp(-1, -2));

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(68);
        time.setTextColor(Ui.color(this, R.color.text_primary));
        time.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        time.setGravity(Gravity.CENTER);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        date.setTextSize(15);
        date.setTextColor(Ui.color(this, R.color.text_secondary));
        date.setGravity(Gravity.CENTER);
        content.addView(time, Ui.lp(-1, -2));
        content.addView(date, Ui.lp(-1, -2));

        content.addView(new View(this), Ui.weight(1));
        content.addView(np.art);
        LinearLayout.LayoutParams ip = Ui.lp(-1, -2);
        ip.topMargin = Ui.dp(this, 22);
        content.addView(np.info, ip);
        LinearLayout.LayoutParams sp = Ui.lp(-1, -2);
        sp.topMargin = Ui.dp(this, 14);
        content.addView(np.seekBlock, sp);
        content.addView(np.controls, Ui.lp(-1, -2));
        content.addView(new View(this), Ui.weight(1));

        LinearLayout hint = new LinearLayout(this);
        hint.setGravity(Gravity.CENTER);
        hint.setOrientation(LinearLayout.VERTICAL);
        ImageView up = new ImageView(this);
        up.setImageResource(R.drawable.ic_arrow_down);
        up.setRotation(180f);
        Ui.tint(up, R.color.text_secondary);
        hint.addView(up, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
        hint.addView(Ui.text(this, getString(R.string.lock_hint), 13, R.color.text_secondary));
        hint.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 22));
        hint.animate().alpha(0.45f).setDuration(1100).withEndAction(new Runnable() {
            @Override
            public void run() {
                float a = hint.getAlpha() > 0.7f ? 0.45f : 1f;
                hint.animate().alpha(a).setDuration(1100).withEndAction(this).start();
            }
        }).start();
        content.addView(hint, Ui.lp(-1, -2));

        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(Ui.dp(this, 28) + bars.left, bars.top + Ui.dp(this, 8), Ui.dp(this, 28) + bars.right, bars.bottom);
            return insets;
        });

        np.artCb = this::backdrop;
        setupSwipe(root);
        ContextCompat.registerReceiver(this, unlocked, new IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void backdrop(Uri u) {
        if (u == null) {
            bg.setImageDrawable(null);
            return;
        }
        Art.fetch(this, u, 256, (Bitmap bmp) -> {
            if (isDestroyed()) return;
            bg.setImageBitmap(bmp);
        });
    }

    /** Drag the whole player upward; past the threshold the keyguard is dismissed (PIN/biometric if set). */
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
                    content.setAlpha(Math.max(0.25f, 1f + dy / Ui.dp(this, 520)));
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
        try {
            unregisterReceiver(unlocked);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
