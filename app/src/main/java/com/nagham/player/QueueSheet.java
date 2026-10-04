package com.nagham.player;

import android.app.Dialog;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.ArrayList;
import java.util.List;

/**
 * The play queue as a tall sheet in the app's own style: cover, title, artist, equalizer on the current song.
 * Drag the handle to reorder, swipe a row away to remove it, tap to jump. Changes go straight to the player.
 */
public final class QueueSheet {
    private QueueSheet() {
    }

    public static void show(final Context c) {
        final MediaController m = Pb.get();
        if (m == null || m.getMediaItemCount() == 0) {
            Ui.toast(c, R.string.nothing_playing);
            return;
        }
        final Dialog d = new Dialog(c, R.style.AppSheet);
        final LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_sheet);
        root.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 10));

        // ---- header (drag it down to dismiss)
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        View grab = new View(c);
        grab.setBackgroundResource(R.drawable.bg_grabber);
        LinearLayout.LayoutParams gp = Ui.lp(Ui.dp(c, 40), Ui.dp(c, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = Ui.dp(c, 4);
        gp.bottomMargin = Ui.dp(c, 10);
        head.addView(grab, gp);
        LinearLayout titleRow = new LinearLayout(c);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPaddingRelative(Ui.dp(c, 24), Ui.dp(c, 4), Ui.dp(c, 24), Ui.dp(c, 12));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(c, c.getString(R.string.up_next), 22, R.color.text_primary);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        TextView hint = Ui.text(c, c.getString(R.string.queue_hint), 12, R.color.text_secondary);
        hint.setPadding(0, Ui.dp(c, 3), 0, 0);
        col.addView(title);
        col.addView(hint);
        titleRow.addView(col, Ui.weight(1));
        final TextView count = Ui.text(c, "", 12, R.color.accent_text);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        count.setBackgroundResource(R.drawable.bg_chip_soft);
        count.setPadding(Ui.dp(c, 12), Ui.dp(c, 5), Ui.dp(c, 12), Ui.dp(c, 5));
        titleRow.addView(count);
        head.addView(titleRow);
        root.addView(head);
        final float[] y0 = {0};
        head.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    y0[0] = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    root.setTranslationY(Math.max(0f, e.getRawY() - y0[0]));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (root.getTranslationY() > Ui.dp(c, 110)) d.dismiss();
                    else root.animate().translationY(0f).setDuration(220).start();
                    return true;
                default:
                    return false;
            }
        });

        // ---- list
        final Adapter ad = new Adapter(c, m, count);
        final RecyclerView rv = new RecyclerView(c);
        final LinearLayoutManager lm = new LinearLayoutManager(c);
        rv.setLayoutManager(lm);
        rv.setAdapter(ad);
        rv.setHasFixedSize(true);
        rv.setClipToPadding(false);
        rv.setPadding(0, 0, 0, Ui.dp(c, 12));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        if (rv.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) rv.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                return ad.move(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                ad.remove(vh.getBindingAdapterPosition());
                if (ad.getItemCount() == 0) d.dismiss();
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int state) {
                super.onSelectedChanged(vh, state);
                if (vh != null && state == ItemTouchHelper.ACTION_STATE_DRAG) {
                    vh.itemView.animate().scaleX(1.03f).scaleY(1.03f).alpha(0.94f).setDuration(120).start();
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(r, vh);
                vh.itemView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                ad.commitMove();
            }
        });
        helper.attachToRecyclerView(rv);
        ad.helper = helper;
        root.addView(rv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- keep the highlighted row in step with the player while the sheet is open
        final Player.Listener pl = new Player.Listener() {
            @Override
            public void onMediaItemTransition(MediaItem item, int reason) {
                ad.syncCurrent();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                ad.syncPlaying();
            }
        };
        Pb.add(pl);
        d.setOnDismissListener(x -> Pb.remove(pl));

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.84f));
        }
        d.show();
        lm.scrollToPositionWithOffset(Math.max(0, ad.cur - 1), 0);
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        final Context ctx;
        final MediaController m;
        final TextView count;
        final List<MediaItem> items = new ArrayList<>();
        int cur, dragFrom = -1, dragTo = -1;
        boolean playing;
        ItemTouchHelper helper;

        Adapter(Context c, MediaController m, TextView count) {
            this.ctx = c;
            this.m = m;
            this.count = count;
            for (int i = 0; i < m.getMediaItemCount(); i++) items.add(m.getMediaItemAt(i));
            cur = m.getCurrentMediaItemIndex();
            playing = m.isPlaying();
            updateCount();
        }

        void updateCount() {
            count.setText(ctx.getResources().getQuantityString(R.plurals.songs_n, items.size(), items.size()));
        }

        /** Local reorder while dragging; the player is told once, when the finger lifts. */
        boolean move(int a, int b) {
            if (a < 0 || b < 0 || a >= items.size() || b >= items.size()) return false;
            if (dragFrom < 0) dragFrom = a;
            dragTo = b;
            items.add(b, items.remove(a));
            if (cur == a) cur = b;
            else if (a < cur && b >= cur) cur--;
            else if (a > cur && b <= cur) cur++;
            notifyItemMoved(a, b);
            return true;
        }

        @SuppressLint("NotifyDataSetChanged")
        void commitMove() {
            if (dragFrom >= 0 && dragFrom != dragTo) {
                m.moveMediaItem(dragFrom, dragTo);
                cur = m.getCurrentMediaItemIndex();
            }
            dragFrom = dragTo = -1;
            notifyDataSetChanged(); // refresh first / last corner shapes
        }

        @SuppressLint("NotifyDataSetChanged")
        void remove(int p) {
            if (p < 0 || p >= items.size()) return;
            m.removeMediaItem(p);
            items.remove(p);
            cur = Math.min(m.getCurrentMediaItemIndex(), Math.max(0, items.size() - 1));
            updateCount();
            notifyDataSetChanged();
        }

        void syncCurrent() {
            int old = cur;
            cur = m.getCurrentMediaItemIndex();
            if (old >= 0 && old < items.size()) notifyItemChanged(old);
            if (cur >= 0 && cur < items.size()) notifyItemChanged(cur);
        }

        void syncPlaying() {
            playing = m.isPlaying();
            if (cur >= 0 && cur < items.size()) notifyItemChanged(cur);
        }

        static final class VH extends RecyclerView.ViewHolder {
            final LinearLayout card;
            final ImageView art;
            final View scrim;
            final EqView eq;
            final TextView title, sub;
            final ImageButton handle;

            VH(FrameLayout root, LinearLayout card, ImageView art, View scrim, EqView eq, TextView title, TextView sub, ImageButton handle) {
                super(root);
                this.card = card;
                this.art = art;
                this.scrim = scrim;
                this.eq = eq;
                this.title = title;
                this.sub = sub;
                this.handle = handle;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            FrameLayout root = new FrameLayout(c);
            root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.setPadding(Ui.dp(c, 14), Ui.dp(c, 1), Ui.dp(c, 14), Ui.dp(c, 1));
            LinearLayout card = new LinearLayout(c);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPaddingRelative(Ui.dp(c, 12), Ui.dp(c, 9), Ui.dp(c, 4), Ui.dp(c, 9));
            card.setDuplicateParentStateEnabled(true);
            root.addView(card, new FrameLayout.LayoutParams(-1, -2));

            ImageView art = Ui.artView(c, 48, 13);
            FrameLayout artBox = new FrameLayout(c);
            artBox.addView(art);
            View scrim = new View(c);
            scrim.setBackgroundColor(0x88000000);
            Ui.round(scrim, Ui.dp(c, 13));
            scrim.setVisibility(View.GONE);
            artBox.addView(scrim, new FrameLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
            EqView eq = new EqView(c);
            eq.setVisibility(View.GONE);
            artBox.addView(eq, new FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER));
            card.addView(artBox, Ui.lp(Ui.dp(c, 48), Ui.dp(c, 48)));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView title = Ui.text(c, "", 16, R.color.text_primary);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView sub = Ui.text(c, "", 13, R.color.text_secondary);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setPadding(0, Ui.dp(c, 2), 0, 0);
            col.addView(title);
            col.addView(sub);
            LinearLayout.LayoutParams cp = Ui.weight(1);
            cp.setMarginStart(Ui.dp(c, 14));
            cp.setMarginEnd(Ui.dp(c, 4));
            card.addView(col, cp);

            ImageButton handle = Ui.flat(c, R.drawable.ic_drag, 48, R.string.reorder, null);
            Ui.tint(handle, R.color.text_secondary);
            card.addView(handle);
            return new VH(root, card, art, scrim, eq, title, sub, handle);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull final VH h, int pos) {
            MediaItem it = items.get(pos);
            MediaMetadata md = it.mediaMetadata;
            boolean isCur = pos == cur;
            h.title.setText(md.title == null ? "" : md.title);
            h.title.setTextColor(Ui.color(ctx, isCur ? R.color.accent_text : R.color.text_primary));
            h.sub.setText(Fmt.artist(ctx, md.artist == null ? null : md.artist.toString()));
            Art.load(ctx, md.artworkUri, h.art, Ui.dp(ctx, 48));
            Ui.shape(ctx, h.card, pos == 0, pos == items.size() - 1, isCur ? R.color.accent_soft : R.color.surface);
            h.scrim.setVisibility(isCur ? View.VISIBLE : View.GONE);
            h.eq.setVisibility(isCur ? View.VISIBLE : View.GONE);
            h.eq.setAnimating(isCur && playing);
            h.itemView.setOnClickListener(v -> {
                int p = h.getBindingAdapterPosition();
                if (p < 0) return;
                m.seekToDefaultPosition(p);
                m.play();
            });
            h.handle.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN && helper != null) {
                    Ui.tap(v);
                    helper.startDrag(h);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
