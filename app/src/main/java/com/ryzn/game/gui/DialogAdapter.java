package com.ryzn.game.gui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ryzn.game.R;
import com.ryzn.game.gui.util.Utils;

import java.util.List;

public class DialogAdapter extends RecyclerView.Adapter<DialogAdapter.ViewHolder> {

    public interface OnClickListener {
        void onClick(int position, String col0Text);
    }

    public interface OnDoubleClickListener {
        void onDoubleClick();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView[] mFields = new TextView[4];
        public final ImageView mRowBg;
        private final View mRoot;

        public ViewHolder(@NonNull View view) {
            super(view);
            mRoot = view;
            mRowBg = view.findViewById(R.id.item_bg);
            mFields[0] = view.findViewById(R.id.item_field1);
            mFields[1] = view.findViewById(R.id.item_field2);
            mFields[2] = view.findViewById(R.id.item_field3);
            mFields[3] = view.findViewById(R.id.item_field4);
        }
    }

    private final List<String[]> mRows;
    private final int mColumnCount;
    private final int[] mColumnWidths;
    private boolean mMatchParent = false;
    private int mSelectedPosition = 0;

    private OnClickListener mOnClickListener;
    private OnDoubleClickListener mOnDoubleClickListener;

    public DialogAdapter(List<String[]> rows, int columnCount, int[] columnWidths) {
        this.mRows = rows;
        this.mColumnCount = Math.max(1, Math.min(4, columnCount));
        this.mColumnWidths = columnWidths != null ? columnWidths : new int[]{0, 0, 0, 0};
    }

    public void setOnClickListener(OnClickListener listener) {
        this.mOnClickListener = listener;
    }

    public void setOnDoubleClickListener(OnDoubleClickListener listener) {
        this.mOnDoubleClickListener = listener;
    }

    public void setMatchParent(boolean matchParent) {
        if (this.mMatchParent != matchParent) {
            this.mMatchParent = matchParent;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_dialog_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int pos = holder.getAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) pos = position;
        if (pos < 0 || pos >= mRows.size()) return;

        String[] cols = mRows.get(pos);
        for (int c = 0; c < 4; c++) {
            TextView tv = holder.mFields[c];
            if (c >= mColumnCount) {
                tv.setVisibility(View.GONE);
            } else {
                tv.setVisibility(View.VISIBLE);
                String cell = c < cols.length ? cols[c] : "";
                tv.setText(Utils.getColoredString(cell));

                ViewGroup.LayoutParams lp = tv.getLayoutParams();
                if (mColumnWidths[c] > 0) {
                    lp.width = mColumnWidths[c];
                } else {
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                tv.setLayoutParams(lp);
            }
        }

        holder.mRowBg.setVisibility(pos == mSelectedPosition ? View.VISIBLE : View.GONE);

        ViewGroup.LayoutParams rootLp = holder.mRoot.getLayoutParams();
        if (rootLp != null) {
            rootLp.width = mMatchParent
                    ? ViewGroup.LayoutParams.MATCH_PARENT
                    : ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.mRoot.setLayoutParams(rootLp);
        }

        holder.mRoot.setOnClickListener(v -> {
            int clickedPos = holder.getAdapterPosition();
            if (clickedPos == RecyclerView.NO_POSITION || clickedPos >= mRows.size()) return;
            if (clickedPos != mSelectedPosition) {
                int prev = mSelectedPosition;
                mSelectedPosition = clickedPos;
                if (prev >= 0 && prev < mRows.size()) {
                    notifyItemChanged(prev);
                }
                notifyItemChanged(clickedPos);
                if (mOnClickListener != null) {
                    String[] row = mRows.get(clickedPos);
                    String firstCol = row.length > 0 ? Utils.getStringWithoutColors(row[0]) : "";
                    mOnClickListener.onClick(clickedPos, firstCol);
                }
            } else if (mOnDoubleClickListener != null) {
                mOnDoubleClickListener.onDoubleClick();
            }
        });
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }
}
