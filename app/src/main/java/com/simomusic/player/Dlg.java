package com.simomusic.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.function.Consumer;

/** Centered card dialogs in the app's own style (replaces the stock AlertDialog). */
public final class Dlg {
    private Dlg() {
    }

    /**
     * Closes the dialog with its screen, so nothing leaks when the activity goes away under an open dialog.
     * Owns the dismiss listener: onDismiss (may be null) runs when the dialog closes, for any reason.
     */
    public static void bind(Context c, final Dialog d, final Runnable onDismiss) {
        if (c instanceof LifecycleOwner) {
            final LifecycleOwner o = (LifecycleOwner) c;
            final DefaultLifecycleObserver ob = new DefaultLifecycleObserver() {
                @Override
                public void onDestroy(@NonNull LifecycleOwner owner) {
                    try {
                        d.dismiss();
                    } catch (Exception ignored) {
                    }
                }
            };
            o.getLifecycle().addObserver(ob);
            d.setOnDismissListener(x -> {
                o.getLifecycle().removeObserver(ob);
                if (onDismiss != null) onDismiss.run();
            });
        } else if (onDismiss != null) {
            d.setOnDismissListener(x -> onDismiss.run());
        }
    }

    /** True when it is unsafe to show a window from this context. */
    public static boolean dead(Context c) {
        if (c instanceof Activity) {
            Activity a = (Activity) c;
            return a.isFinishing() || a.isDestroyed();
        }
        return false;
    }

    private static Dialog base(Context c, CharSequence title, CharSequence message, LinearLayout[] holder) {
        final Dialog d = new Dialog(c, R.style.AppCard);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog_card);
        root.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 20));
        if (title != null) {
            TextView t = Ui.text(c, title, 22, R.color.text_primary);
            t.setTypeface(Ui.titleFace(true));
            root.addView(t, Ui.lp(-1, -2));
        }
        if (message != null) {
            TextView m = Ui.text(c, message, 15, R.color.text_secondary);
            m.setLineSpacing(0, 1.12f);
            LinearLayout.LayoutParams mp = Ui.lp(-1, -2);
            mp.topMargin = Ui.dp(c, title != null ? 8 : 0);
            root.addView(m, mp);
        }
        holder[0] = root;
        return d;
    }

    private static void buttons(Context c, LinearLayout root, Button no, Button yes) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1f);
        a.setMarginEnd(Ui.dp(c, 10));
        row.addView(no, a);
        row.addView(yes, new LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1f));
        LinearLayout.LayoutParams rp = Ui.lp(-1, -2);
        rp.topMargin = Ui.dp(c, 22);
        root.addView(row, rp);
    }

    private static void finish(Context c, Dialog d, LinearLayout root) {
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    public static void confirm(final Context c, CharSequence title, CharSequence message, int okRes, boolean danger, final Runnable ok) {
        if (dead(c)) return;
        LinearLayout[] h = new LinearLayout[1];
        final Dialog d = base(c, title, message, h);
        Button no = Ui.pill(c, R.string.cancel, 0, false, v -> d.dismiss());
        Button yes = Ui.pill(c, okRes, 0, true, v -> {
            d.dismiss();
            if (ok != null) ok.run();
        });
        if (danger) yes.setBackgroundResource(R.drawable.btn_danger);
        no.setPaddingRelative(0, 0, 0, 0);
        yes.setPaddingRelative(0, 0, 0, 0);
        no.setGravity(Gravity.CENTER);
        yes.setGravity(Gravity.CENTER);
        buttons(c, h[0], no, yes);
        finish(c, d, h[0]);
        bind(c, d, null);
        d.show();
    }

    public static void input(final Context c, int titleRes, String initial, int okRes, final Consumer<String> ok) {
        if (dead(c)) return;
        LinearLayout[] h = new LinearLayout[1];
        final Dialog d = base(c, c.getString(titleRes), null, h);
        final EditText e = Ui.edit(c, c.getString(R.string.playlist_name), initial);
        LinearLayout.LayoutParams ep = Ui.lp(-1, -2);
        ep.topMargin = Ui.dp(c, 16);
        e.setLayoutParams(ep);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        h[0].addView(e);
        final Runnable submit = () -> {
            String s = e.getText().toString().trim();
            if (s.isEmpty()) {
                e.requestFocus();
                Ui.tap(e);
                return;
            }
            hide(c, e);
            d.dismiss();
            ok.accept(s);
        };
        e.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });
        Button no = Ui.pill(c, R.string.cancel, 0, false, v -> {
            hide(c, e);
            d.dismiss();
        });
        Button yes = Ui.pill(c, okRes, 0, true, v -> submit.run());
        no.setPaddingRelative(0, 0, 0, 0);
        yes.setPaddingRelative(0, 0, 0, 0);
        no.setGravity(Gravity.CENTER);
        yes.setGravity(Gravity.CENTER);
        buttons(c, h[0], no, yes);
        finish(c, d, h[0]);
        Window w = d.getWindow();
        if (w != null) {
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        bind(c, d, null);
        d.show();
        e.setSelection(e.getText().length());
    }

    private static void hide(Context c, View v) {
        InputMethodManager im = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
