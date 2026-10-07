package com.simomusic.player;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Full-screen player for launches from the notification or the lock screen. It is translucent, rises from the bottom,
 * and can be dragged back down to close.
 */
public class PlayerActivity extends AppCompatActivity implements FullBleed {
    private PlayerPanel panel;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.transitions(this, 0, 0, 0, 0);
        Pb.connect(this);
        FrameLayout root = new FrameLayout(this);
        panel = new PlayerPanel(this, new PlayerPanel.Host() {
            @Override
            public void onCollapsed() {
                finish();
            }

            @Override
            public void onProgress(float f) {
            }

            @Override
            public void onState(boolean expanded) {
            }
        });
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        panel.setCollapsedY(getResources().getDisplayMetrics().heightPixels);
        panel.expand(true);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                panel.collapse(true);
            }
        });
    }

    @Override
    public void finish() {
        super.finish();
        Ui.legacyClose(this, 0, 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Pb.connect(this);
        panel.onHostStart();
    }

    @Override
    protected void onStop() {
        panel.onHostStop();
        super.onStop();
    }
}
