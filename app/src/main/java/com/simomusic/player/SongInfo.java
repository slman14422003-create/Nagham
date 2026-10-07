package com.simomusic.player;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** File details (format, bitrate, sample rate, size, location) and sharing for one song. */
public final class SongInfo {
    private SongInfo() {
    }

    public static void share(Context c, Track t) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND).setType("audio/*")
                    .putExtra(Intent.EXTRA_STREAM, t.uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent ch = Intent.createChooser(i, c.getString(R.string.share));
            if (!(c instanceof android.app.Activity)) ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(ch);
        } catch (Exception ignored) {
        }
    }

    private static final class Data {
        String name = "", folder = "", mime = "";
        long size = -1;
        int rate, channels, bitrate;
    }

    public static void show(final Context c, final Track t) {
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
        TextView title = Ui.text(c, t.title, 17, R.color.text_primary);
        title.setTypeface(Ui.titleFace(true));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 10));
        head.addView(title);
        root.addView(head);
        Ui.dragDismiss(head, root, d);

        final int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.72f);
        ScrollView sv = new ScrollView(c) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        final LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
        sv.addView(body);
        root.addView(sv);
        TextView loading = Ui.text(c, c.getString(R.string.bt_loading), 15, R.color.text_secondary);
        loading.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        body.addView(loading);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        final boolean[] alive = {true};
        Dlg.bind(c, d, () -> alive[0] = false);
        d.show();

        new Thread(() -> {
            final Data x = read(c, t);
            body.post(() -> {
                if (!alive[0] || !d.isShowing()) return;
                body.removeAllViews();
                LinearLayout facts = new LinearLayout(c);
                facts.setOrientation(LinearLayout.VERTICAL);
                facts.setBackgroundResource(R.drawable.bg_card);
                facts.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6));
                BtSheet.addFact(c, facts, R.string.si_title, t.title);
                BtSheet.addFact(c, facts, R.string.si_artist, t.artist);
                if (t.album != null && !t.album.isEmpty()) BtSheet.addFact(c, facts, R.string.si_album, t.album);
                BtSheet.addFact(c, facts, R.string.si_duration, Fmt.time(t.duration));
                String fmt = (t.ext == null ? "" : t.ext.toUpperCase(Locale.ROOT))
                        + (x.mime.isEmpty() ? "" : (t.ext == null || t.ext.isEmpty() ? "" : " · ") + x.mime);
                if (!fmt.isEmpty()) BtSheet.addFact(c, facts, R.string.si_format, fmt);
                if (x.bitrate > 0) BtSheet.addFact(c, facts, R.string.si_bitrate, x.bitrate + " kbps");
                if (x.rate > 0) BtSheet.addFact(c, facts, R.string.si_rate, String.format(Locale.US, "%.1f kHz", x.rate / 1000f));
                if (x.channels > 0) BtSheet.addFact(c, facts, R.string.si_channels,
                        x.channels == 1 ? c.getString(R.string.bt_mono) : x.channels == 2 ? c.getString(R.string.bt_stereo) : String.valueOf(x.channels));
                if (x.size > 0) BtSheet.addFact(c, facts, R.string.si_size, String.format(Locale.US, "%.1f MB", x.size / 1048576f));
                if (!x.name.isEmpty()) BtSheet.addFact(c, facts, R.string.si_file, x.name);
                if (!x.folder.isEmpty()) BtSheet.addFact(c, facts, R.string.si_folder, x.folder);
                body.addView(facts);
            });
        }).start();
    }

    private static Data read(Context c, Track t) {
        Data x = new Data();
        try (Cursor q = c.getContentResolver().query(t.uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (q != null && q.moveToFirst()) {
                x.name = q.isNull(0) ? "" : q.getString(0);
                x.size = q.isNull(1) ? -1 : q.getLong(1);
            }
        } catch (Exception ignored) {
        }
        String col = Build.VERSION.SDK_INT >= 29 ? MediaStore.MediaColumns.RELATIVE_PATH : MediaStore.MediaColumns.DATA;
        try (Cursor q = c.getContentResolver().query(t.uri, new String[]{col}, null, null, null)) {
            if (q != null && q.moveToFirst() && !q.isNull(0)) {
                String p = q.getString(0);
                if (Build.VERSION.SDK_INT < 29 && p.contains("/")) p = p.substring(0, p.lastIndexOf('/'));
                x.folder = p;
            }
        } catch (Exception ignored) {
        }
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(c, t.uri, null);
            if (ex.getTrackCount() > 0) {
                MediaFormat f = ex.getTrackFormat(0);
                if (f.containsKey(MediaFormat.KEY_MIME)) x.mime = f.getString(MediaFormat.KEY_MIME);
                if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) x.rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) x.channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                if (f.containsKey(MediaFormat.KEY_BIT_RATE)) x.bitrate = f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                ex.release();
            } catch (Exception ignored) {
            }
        }
        if (x.bitrate <= 0 && x.size > 0 && t.duration > 0) x.bitrate = (int) (x.size * 8L / t.duration);   // bytes*8 / ms = kbit/s
        return x;
    }
}
