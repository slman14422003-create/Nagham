package com.nagham.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
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

/** Song rows grouped into rounded cards. Three modes: normal (⋮ menu), select (check marks), reorder (drag handle). */
public final class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.VH> {
    public interface Listener {
        void onClick(Track t, int pos);

        void onMore(Track t, int pos);
    }

    public final List<Track> data = new ArrayList<>();
    public final Set<Long> selected = new HashSet<>();
    public boolean select, reorder;
    public ItemTouchHelper helper;
    private long current = -1;
    private final Listener l;
    private final Context ctx;

    public TrackAdapter(Context c, Listener l) {
        this.ctx = c;
        this.l = l;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<Track> list) {
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    public void setCurrent(long id) {
        if (id == current) return;
        current = id;
        notifyItemRangeChanged(0, data.size());
    }

    public void toggle(int pos) {
        long id = data.get(pos).id;
        if (!selected.remove(id)) selected.add(id);
        notifyItemChanged(pos);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final ImageView art, check;
        final TextView title, sub;
        final ImageButton more, handle;

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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        Context c = parent.getContext();
        FrameLayout root = new FrameLayout(c);
        root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setPadding(Ui.dp(c, 14), Ui.dp(c, 1), Ui.dp(c, 14), Ui.dp(c, 1));
        LinearLayout card = new LinearLayout(c);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPaddingRelative(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10));
        card.setDuplicateParentStateEnabled(true);
        root.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView art = Ui.artView(c, 50, 14);
        card.addView(art);
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
        cp.setMarginEnd(Ui.dp(c, 6));
        card.addView(col, cp);

        ImageView check = new ImageView(c);
        check.setImageResource(R.drawable.ic_check);
        check.setBackgroundResource(R.drawable.bg_circle_check);
        check.setPadding(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5));
        LinearLayout.LayoutParams kp = Ui.lp(Ui.dp(c, 28), Ui.dp(c, 28));
        kp.setMarginEnd(Ui.dp(c, 10));
        card.addView(check, kp);
        ImageButton more = Ui.flat(c, R.drawable.ic_more, 44, R.string.more, null);
        Ui.tint(more, R.color.text_secondary);
        card.addView(more);
        ImageButton handle = Ui.flat(c, R.drawable.ic_sort, 44, R.string.reorder, null);
        Ui.tint(handle, R.color.text_secondary);
        card.addView(handle);
        return new VH(root, card, art, title, sub, more, handle, check);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull final VH h, int pos) {
        final Track t = data.get(pos);
        boolean playing = t.id == current;
        boolean sel = select && selected.contains(t.id);
        h.title.setText(t.title);
        h.title.setTextColor(Ui.color(ctx, playing ? R.color.accent_text : R.color.text_primary));
        h.sub.setText(Fmt.artist(ctx, t.artist) + " · " + Fmt.time(t.duration));
        Art.load(ctx, t.uri, h.art, Ui.dp(ctx, 50));
        Ui.shape(ctx, h.card, pos == 0, pos == data.size() - 1, sel || playing ? R.color.accent_soft : R.color.surface);
        h.check.setVisibility(select ? android.view.View.VISIBLE : android.view.View.GONE);
        h.check.setSelected(sel);
        Ui.tint(h.check, sel ? R.color.on_accent : R.color.text_hint);
        h.more.setVisibility(select || reorder ? android.view.View.GONE : android.view.View.VISIBLE);
        h.handle.setVisibility(reorder ? android.view.View.VISIBLE : android.view.View.GONE);
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p < 0) return;
            if (select) toggle(p);
            l.onClick(data.get(p), p);
        });
        h.more.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p >= 0) l.onMore(data.get(p), p);
        });
        h.handle.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && helper != null) helper.startDrag(h);
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
