package com.simomusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Swipe a song row to either side to play it next: an accent pill with the queue icon follows the finger, the row
 * springs back, and a short tap and a toast confirm. Does nothing while selecting or reordering.
 */
public final class SwipeToQueue extends ItemTouchHelper.SimpleCallback {
    private final Context c;
    private final TrackAdapter adapter;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Drawable icon;
    private final int radius, inset, iconSize;

    public SwipeToQueue(Context c, TrackAdapter adapter) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.c = c;
        this.adapter = adapter;
        bg.setColor(Ui.color(c, R.color.accent));
        Drawable d = ContextCompat.getDrawable(c, R.drawable.ic_queue_add);
        if (d != null) {
            d = d.mutate();
            d.setTint(Ui.color(c, R.color.on_accent));
        }
        icon = d;
        radius = Ui.dp(c, 18);
        inset = Ui.dp(c, 14);
        iconSize = Ui.dp(c, 24);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
        return false;
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        if (rv.getAdapter() != adapter || adapter.select || adapter.reorder) return 0;
        if (vh.getItemViewType() == 1) return 0;      // the header that scrolls with the list
        return super.getSwipeDirs(rv, vh);
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder vh) {
        return 0.32f;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        int adapterPos = vh.getBindingAdapterPosition();
        if (adapterPos == RecyclerView.NO_POSITION) return;
        int p = adapterPos - (adapter.header == null ? 0 : 1);
        adapter.notifyItemChanged(adapterPos);          // the row springs back into place
        if (p >= 0 && p < adapter.data.size()) {
            Pb.next(c, adapter.data.get(p));
            Ui.tap(vh.itemView);
            Ui.toast(c, R.string.added_queue);
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                            float dX, float dY, int state, boolean active) {
        if (state == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
            View v = vh.itemView;
            float top = v.getTop() + Ui.dp(c, 1), bottom = v.getBottom() - Ui.dp(c, 1);
            if (dX > 0) rect.set(v.getLeft() + inset, top, v.getLeft() + inset + dX, bottom);
            else rect.set(v.getRight() - inset + dX, top, v.getRight() - inset, bottom);
            if (rect.width() > 2f) {
                canvas.drawRoundRect(rect, radius, radius, bg);
                if (icon != null && rect.width() > iconSize + inset) {
                    int cy = (int) ((top + bottom) / 2f);
                    int cx = dX > 0 ? (int) rect.left + inset + iconSize / 2 : (int) rect.right - inset - iconSize / 2;
                    icon.setBounds(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2);
                    icon.draw(canvas);
                }
            }
        }
        super.onChildDraw(canvas, rv, vh, dX, dY, state, active);
    }
}
