package com.simomusic.player;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Settings > Updates: check the latest GitHub release, download it and install it without leaving the app. */
public final class UpdateSheet {
    private UpdateSheet() {
    }

    public static void show(final Context c) {
        if (Dlg.dead(c)) return;
        final Dialog d = new Dialog(c, R.style.AppSheet);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_sheet);
        root.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 24));

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        View grab = new View(c);
        grab.setBackgroundResource(R.drawable.bg_grabber);
        LinearLayout.LayoutParams gp = Ui.lp(Ui.dp(c, 40), Ui.dp(c, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = Ui.dp(c, 4);
        gp.bottomMargin = Ui.dp(c, 8);
        head.addView(grab, gp);
        TextView t = Ui.text(c, c.getString(R.string.upd_title), 17, R.color.text_primary);
        t.setTypeface(Typeface.create("serif", Typeface.BOLD));
        t.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 10));
        head.addView(t);
        root.addView(head);
        Ui.dragDismiss(head, root, d);

        final int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.80f);
        ScrollView sv = new ScrollView(c) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        final LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
        sv.addView(body);
        root.addView(sv);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        final AtomicBoolean cancel = new AtomicBoolean(false);
        final boolean[] alive = {true};
        Dlg.bind(c, d, () -> {
            alive[0] = false;
            cancel.set(true);
        });
        d.show();
        check(c, body, alive, cancel);
    }

    private static void check(final Context c, final LinearLayout body, final boolean[] alive, final AtomicBoolean cancel) {
        body.removeAllViews();
        body.addView(line(c, c.getString(R.string.upd_current, Updater.current(c)), R.color.text_secondary, 14));
        body.addView(line(c, c.getString(R.string.upd_checking), R.color.text_primary, 16));
        Updater.check(c, (info, err) -> {
            if (!alive[0]) return;
            body.removeAllViews();
            body.addView(line(c, c.getString(R.string.upd_current, Updater.current(c)), R.color.text_secondary, 14));
            if (err != 0 || info == null) {
                body.addView(line(c, errorText(c, err), R.color.text_primary, 16));
                body.addView(button(c, R.string.upd_retry, false, v -> check(c, body, alive, cancel)));
                return;
            }
            if (!info.newer) {
                body.addView(line(c, c.getString(R.string.upd_latest, Updater.current(c)), R.color.text_primary, 16));
                body.addView(button(c, R.string.upd_check_again, false, v -> check(c, body, alive, cancel)));
                return;
            }
            available(c, body, info, alive, cancel);
        });
    }

    private static void available(final Context c, final LinearLayout body, final Updater.Info info,
                                  final boolean[] alive, final AtomicBoolean cancel) {
        String ver = info.tag.isEmpty() ? info.name : info.tag;
        TextView title = line(c, c.getString(R.string.upd_available, ver), R.color.accent_text, 18);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        body.addView(title);
        if (info.size > 0) {
            body.addView(line(c, String.format(Locale.US, "%.1f MB", info.size / 1048576f), R.color.text_secondary, 13));
        }
        String notes = info.notes == null ? "" : info.notes.trim();
        if (!notes.isEmpty()) {
            if (notes.length() > 700) notes = notes.substring(0, 700) + "…";
            TextView nh = line(c, c.getString(R.string.upd_notes), R.color.text_secondary, 13);
            nh.setTypeface(Typeface.DEFAULT_BOLD);
            body.addView(nh);
            TextView nt = line(c, notes, R.color.text_primary, 14);
            nt.setLineSpacing(0, 1.1f);
            body.addView(nt);
        }
        body.addView(button(c, R.string.upd_download, true, v -> startDownload(c, body, info, alive, cancel)));
    }

    private static void startDownload(final Context c, final LinearLayout body, final Updater.Info info,
                                      final boolean[] alive, final AtomicBoolean cancel) {
        body.removeAllViews();
        final TextView label = line(c, c.getString(R.string.upd_downloading, 0), R.color.text_primary, 16);
        body.addView(label);
        final ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.color(c, R.color.accent)));
        LinearLayout.LayoutParams bp = Ui.lp(-1, Ui.dp(c, 8));
        bp.topMargin = Ui.dp(c, 12);
        body.addView(bar, bp);
        Updater.download(c, info, cancel, new Updater.DownloadCb() {
            @Override
            public void progress(int percent) {
                if (!alive[0]) return;
                bar.setProgress(percent);
                label.setText(c.getString(R.string.upd_downloading, percent));
            }

            @Override
            public void done(File apk, int error) {
                if (!alive[0]) return;
                body.removeAllViews();
                if (apk == null) {
                    body.addView(line(c, errorText(c, error), R.color.text_primary, 16));
                    body.addView(button(c, R.string.upd_retry, false, v -> startDownload(c, body, info, alive, cancel)));
                    return;
                }
                readyToInstall(c, body, apk);
            }
        });
    }

    private static void readyToInstall(final Context c, final LinearLayout body, final File apk) {
        body.removeAllViews();
        if (!Updater.canInstall(c)) {
            body.addView(line(c, c.getString(R.string.upd_perm), R.color.text_primary, 16));
            body.addView(line(c, c.getString(R.string.upd_perm_sub), R.color.text_secondary, 13));
            body.addView(button(c, R.string.upd_perm_btn, true, v -> Updater.askInstallPermission(c)));
            body.addView(button(c, R.string.upd_install, false, v -> readyToInstall(c, body, apk)));
            return;
        }
        body.addView(line(c, c.getString(R.string.upd_ready), R.color.text_primary, 16));
        body.addView(button(c, R.string.upd_install, true, v -> {
            if (!Updater.install(c, apk)) Ui.toast(c, R.string.upd_err_file);
        }));
        // straight to the system installer: one tap fewer
        if (!Dlg.dead(c) && !Updater.install(c, apk)) Ui.toast(c, R.string.upd_err_file);
    }

    private static String errorText(Context c, int err) {
        switch (err) {
            case Updater.E_NONE:
                return c.getString(R.string.upd_err_none);
            case Updater.E_LIMIT:
                return c.getString(R.string.upd_err_limit);
            case Updater.E_FILE:
                return c.getString(R.string.upd_err_file);
            default:
                return c.getString(R.string.upd_err_net);
        }
    }

    private static TextView line(Context c, CharSequence text, int color, int sp) {
        TextView t = Ui.text(c, text, sp, color);
        t.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        return t;
    }

    private static Button button(Context c, int textRes, boolean primary, View.OnClickListener l) {
        Button b = Ui.pill(c, textRes, 0, primary, l);
        LinearLayout.LayoutParams p = Ui.lp(-1, Ui.dp(c, 52));
        p.topMargin = Ui.dp(c, 16);
        b.setLayoutParams(p);
        b.setGravity(Gravity.CENTER);
        return b;
    }
}
