package com.simomusic.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Song rows grouped into rounded cards. Modes: normal (⋮ menu), select (check marks), reorder (drag handle).
 * An optional header view scrolls together with the rows.
 */
public final class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.VH> {
    public interface Listener {
        void onClick(Track t, int pos);

        void onMore(Track t, int pos);
    }

    public final List<Track> data = new ArrayList<>();
    public final Set<Long> selected = new HashSet<>();
    public boolean select, reorder;
    public ItemTouchHelper helper;
    public View header;
    private long current = -1;
    private boolean playing;
    private final Listener l;
    private final Context ctx;

    private final int cPrimary, cAccent, cHint;
    private final android.content.res.ColorStateList tOn, tOff;

    public TrackAdapter(Context c, Listener l) {
        this.ctx = c;
        this.l = l;
        cPrimary = Ui.color(c, R.color.text_primary);
        cAccent = Ui.color(c, R.color.accent_text);
        cHint = Ui.color(c, R.color.text_hint);
        tOn = android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.on_accent));
        tOff = android.content.res.ColorStateList.valueOf(cHint);
    }

    private int off() {
        return header == null ? 0 : 1;
    }

    private boolean hadHeader;

    /** Skips the whole re-bind when nothing actually changed (onResume refreshes a lot). */
    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<Track> list) {
        boolean hdr = header != null;
        boolean same = list.size() == data.size() && hdr == hadHeader;
        for (int i = 0; same && i < list.size(); i++) {
            Track a = list.get(i), b = data.get(i);
            same = a.id == b.id && a.title.equals(b.title) && a.artist.equals(b.artist);   // a renamed song redraws too
        }
        if (same) return;
        hadHeader = hdr;
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    /** Play / pause: only the current row's equalizer changes. */
    public void setPlaying(boolean p) {
        if (p == playing) return;
        playing = p;
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).id == current) {
                notifyItemChanged(i + off());
                break;
            }
        }
    }

    /** Only the previously playing and the newly playing rows are re-drawn. */
    public void setCurrent(long id) {
        if (id == current) return;
        long old = current;
        current = id;
        for (int i = 0; i < data.size(); i++) {
            long t = data.get(i).id;
            if (t == old || t == id) notifyItemChanged(i + off());
        }
    }

    public void toggle(int pos) {
        long id = data.get(pos).id;
        if (!selected.remove(id)) selected.add(id);
        notifyItemChanged(pos + off());
    }

    static final class VH extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final ImageView art, check;
        final TextView title, sub;
        final ImageButton more, handle;
        View scrim;
        EqView eq;
        // what this row currently shows: bind only touches what really changed (a fling re-binds rows constantly)
        int shapeState = -1, tint = -1;
        Track bound;
        int titleColor;
        boolean curShown, selShown, selectMode, reorderMode;

        VH(FrameLayout headerHolder) {
            super(headerHolder);
            card = null;
            art = null;
            check = null;
            title = null;
            sub = null;
            more = null;
            handle = null;
        }

        VH(FrameLayout root, LinearLayout card, ImageView art, TextView title, TextView sub, ImageButton more,
           ImageButton handle, ImageView check) {
            super(root);
            this.card = card;
            this.art = art;
            this.title = title;
            this.sub = sub;
            this.more = more;
            this.handle = handle;
            this.check = check;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return header != null && position == 0 ? 1 : 0;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        Context c = parent.getContext();
        FrameLayout root = new FrameLayout(c);
        root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (type == 1) return new VH(root);
        root.setPadding(Ui.dp(c, 14), Ui.dp(c, 1), Ui.dp(c, 14), Ui.dp(c, 1));
        LinearLayout card = new LinearLayout(c);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPaddingRelative(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10));
        card.setDuplicateParentStateEnabled(true);
        root.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView art = Ui.artView(c, 52, 14);
        FrameLayout artBox = new FrameLayout(c);
        artBox.addView(art);
        View scrim = new View(c);
        scrim.setBackgroundColor(0x88000000);
        Ui.round(scrim, Ui.dp(c, 14));
        scrim.setVisibility(View.GONE);
        artBox.addView(scrim, new FrameLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)));
        EqView eq = new EqView(c);
        eq.setVisibility(View.GONE);
        artBox.addView(eq, new FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER));
        card.addView(artBox, Ui.lp(Ui.dp(c, 52), Ui.dp(c, 52)));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(c, "", 16, R.color.text_primary);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView sub = Ui.text(c, "", 13, R.color.text_secondary);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setPadding(0, Ui.dp(c, 3), 0, 0);
        col.addView(title);
        col.addView(sub);
        LinearLayout.LayoutParams cp = Ui.weight(1);
        cp.setMarginStart(Ui.dp(c, 14));
        cp.setMarginEnd(Ui.dp(c, 6));
        card.addView(col, cp);

        ImageView check = new ImageView(c);
        check.setImageResource(R.drawable.ic_check);
        check.setBackgroundResource(R.drawable.bg_circle_check);
        check.setPadding(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5));
        LinearLayout.LayoutParams kp = Ui.lp(Ui.dp(c, 28), Ui.dp(c, 28));
        kp.setMarginEnd(Ui.dp(c, 10));
        card.addView(check, kp);
        ImageButton more = Ui.flat(c, R.drawable.ic_more, 48, R.string.more, null);
        Ui.tint(more, R.color.text_secondary);
        card.addView(more);
        ImageButton handle = Ui.flat(c, R.drawable.ic_sort, 48, R.string.reorder, null);
        Ui.tint(handle, R.color.text_secondary);
        card.addView(handle);
        final VH h = new VH(root, card, art, title, sub, more, handle, check);
        h.scrim = scrim;
        h.eq = eq;
        // listeners are created once per row view, not on every bind
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition() - off();
            if (p < 0 || p >= data.size()) return;
            if (select) toggle(p);
            l.onClick(data.get(p), p);
        });
        h.itemView.setOnLongClickListener(v -> {
            int p = h.getBindingAdapterPosition() - off();
            if (select || reorder || p < 0 || p >= data.size()) return false;
            Ui.tap(v);
            l.onMore(data.get(p), p);
            return true;
        });
        h.more.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition() - off();
            if (p >= 0 && p < data.size()) l.onMore(data.get(p), p);
        });
        h.handle.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && helper != null) helper.startDrag(h);
            return false;
        });
        return h;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull final VH h, int position) {
        if (h.card == null) {
            FrameLayout f = (FrameLayout) h.itemView;
            if (header != null && header.getParent() != f) {
                if (header.getParent() instanceof ViewGroup) ((ViewGroup) header.getParent()).removeView(header);
                f.removeAllViews();
                f.addView(header);
            }
            return;
        }
        final int pos = position - off();
        final Track t = data.get(pos);
        boolean isCur = t.id == current;
        boolean sel = select && selected.contains(t.id);
        if (h.bound != t) {      // a rescan hands out new Track objects, so a renamed song is redrawn too
            h.bound = t;
            h.title.setText(t.title);
            h.sub.setText(Fmt.artist(ctx, t.artist) + " \u00B7 " + Fmt.time(t.duration));
        }
        int tc = isCur ? cAccent : cPrimary;
        if (tc != h.titleColor) {
            h.titleColor = tc;
            h.title.setTextColor(tc);
        }
        Art.load(ctx, t.uri, h.art, Ui.dp(ctx, 52));
        // the card background (a ripple drawable) is rebuilt only when its look really changes
        boolean hi = sel || isCur;
        // the current/selected row keeps the same corners as its neighbours (only the colour changes), so the
        // grouped card list stays one even, coordinated shape instead of a bulging pill between small corners
        boolean top = pos == 0, bottom = pos == data.size() - 1;
        int shape = (top ? 1 : 0) | (bottom ? 2 : 0) | (hi ? 4 : 0);
        if (shape != h.shapeState) {
            h.shapeState = shape;
            Ui.shape(ctx, h.card, top, bottom, hi ? R.color.accent_soft : R.color.surface);
        }
        boolean mark = isCur && !select;
        if (mark != h.curShown || h.tint == -1) {
            h.scrim.setVisibility(mark ? View.VISIBLE : View.GONE);
            h.eq.setVisibility(mark ? View.VISIBLE : View.GONE);
        }
        h.eq.setAnimating(mark && playing);
        if (h.selectMode != select || h.reorderMode != reorder || h.tint == -1) {
            h.check.setVisibility(select ? View.VISIBLE : View.GONE);
            h.more.setVisibility(select || reorder ? View.GONE : View.VISIBLE);
            h.handle.setVisibility(reorder ? View.VISIBLE : View.GONE);
        }
        h.check.setSelected(sel);
        if (select) androidx.core.widget.ImageViewCompat.setImageTintList(h.check, sel ? tOn : tOff);
        h.curShown = mark;
        h.selectMode = select;
        h.reorderMode = reorder;
        h.tint = 0;
    }

    @Override
    public int getItemCount() {
        return data.size() + off();
    }
}
