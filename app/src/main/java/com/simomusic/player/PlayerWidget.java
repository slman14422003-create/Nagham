package com.simomusic.player;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RemoteViews;

import androidx.media3.common.MediaItem;
import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaButtonReceiver;

/**
 * Home-screen widget: song title, artist and previous / play-pause / next. The buttons send the same media keys a
 * headset does, so they work whether the app is open or not; tapping the widget opens the player. The service
 * pushes an update only when the title, artist or play state really changed, and only if a widget is placed.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerWidget extends AppWidgetProvider {
    private static String title = "", artist = "";
    private static boolean playing;
    private static boolean known, exists;

    @Override
    public void onEnabled(Context c) {
        known = true;
        exists = true;
    }

    @Override
    public void onDisabled(Context c) {
        known = true;
        exists = false;
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        known = true;
        exists = ids != null && ids.length > 0;
        for (int id : ids) m.updateAppWidget(id, build(c));
    }

    private static boolean hasWidgets(Context c) {
        if (!known) {
            try {
                int[] ids = AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, PlayerWidget.class));
                exists = ids != null && ids.length > 0;
                known = true;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return exists;
    }

    /** Called by the player service on every player event: cheap when nothing visible changed. */
    public static void sync(Context ctx, Player p) {
        Context c = ctx.getApplicationContext();
        if (!hasWidgets(c)) return;
        MediaItem it = p.getCurrentMediaItem();
        String t = it == null || it.mediaMetadata.title == null ? "" : it.mediaMetadata.title.toString();
        String a = it == null ? "" : Fmt.artist(c, it.mediaMetadata.artist == null ? null : it.mediaMetadata.artist.toString());
        boolean pl = p.getPlayWhenReady() && p.getPlaybackState() != Player.STATE_ENDED && it != null;
        show(c, t, a, pl);
    }

    /** The service stopped: the widget goes back to its resting look. */
    public static void idle(Context ctx) {
        Context c = ctx.getApplicationContext();
        if (hasWidgets(c)) show(c, "", "", false);
    }

    private static void show(Context c, String t, String a, boolean pl) {
        if (t.equals(title) && a.equals(artist) && pl == playing) return;
        title = t;
        artist = a;
        playing = pl;
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, PlayerWidget.class));
            if (ids != null && ids.length > 0) m.updateAppWidget(ids, build(c));
        } catch (RuntimeException e) {
            CrashGuard.nonFatal("widget update", e);
        }
    }

    private static RemoteViews build(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_player);
        rv.setTextViewText(R.id.w_title, title.isEmpty() ? c.getString(R.string.app_name) : title);
        rv.setTextViewText(R.id.w_artist, artist);
        rv.setViewVisibility(R.id.w_artist, artist.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setImageViewResource(R.id.w_play, playing ? R.drawable.ic_pause_fill : R.drawable.ic_play_fill);
        rv.setContentDescription(R.id.w_prev, c.getString(R.string.previous));
        rv.setContentDescription(R.id.w_play, c.getString(R.string.play_pause));
        rv.setContentDescription(R.id.w_next, c.getString(R.string.next));
        rv.setOnClickPendingIntent(R.id.w_prev, key(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 21));
        rv.setOnClickPendingIntent(R.id.w_play, key(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 22));
        rv.setOnClickPendingIntent(R.id.w_next, key(c, KeyEvent.KEYCODE_MEDIA_NEXT, 23));
        PendingIntent open = PendingIntent.getActivity(c, 20, MainActivity.openPlayerIntent(c),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.w_root, open);
        return rv;
    }

    private static PendingIntent key(Context c, int code, int request) {
        Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(new ComponentName(c, MediaButtonReceiver.class))
                .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, code));
        return PendingIntent.getBroadcast(c, request, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
