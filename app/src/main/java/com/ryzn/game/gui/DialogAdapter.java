package com.ryzn.game.gui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ryzn.game.R;
import com.ryzn.game.gui.util.Utils;

import java.util.ArrayList;

public class DialogAdapter extends RecyclerView.Adapter<DialogAdapter.ViewHolder> {
    private final Activity mContext;
    private int mCurrentSelectedPosition = 0;
    private View mCurrentSelectedView;
    private int mFieldsCount = 1;
    private boolean mNeedMatchParent = false;

    private OnClickListener mOnClickListener;
    private OnDoubleClickListener mOnDoubleClickListener;
    private final ArrayList<String> mRowsList;
    private final int[] mTabSizes = {0, 0, 0, 0};
    private final boolean mTablist;
    private boolean mbSkip = false;

    public interface OnClickListener {
        void onClick(int i, String str);
    }

    public interface OnDoubleClickListener {
        void onDoubleClick();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView[] mFields = new TextView[4];
        public final ImageView mRowBg;
        private final View mView;

        public ViewHolder(View view) {
            super(view);
            this.mView = view;
            this.mRowBg = view.findViewById(R.id.item_bg);
            this.mFields[0] = view.findViewById(R.id.item_field1);
            this.mFields[1] = view.findViewById(R.id.item_field2);
            this.mFields[2] = view.findViewById(R.id.item_field3);
            this.mFields[3] = view.findViewById(R.id.item_field4);
        }

        public View getMView() {
            return this.mView;
        }
    }

    public DialogAdapter(ArrayList<String> arrayList, Activity activity, boolean tablist) {
        this.mRowsList = arrayList;
        this.mContext = activity;
        this.mTablist = tablist;
    }

    public void setOnDoubleClickListener(OnDoubleClickListener onDoubleClickListener) {
        this.mOnDoubleClickListener = onDoubleClickListener;
    }

    public void setOnClickListener(OnClickListener onClickListener) {
        this.mOnClickListener = onClickListener;
    }

    public void setMatchParent(boolean matchParent) {
        if (this.mNeedMatchParent != matchParent) {
            this.mNeedMatchParent = matchParent;
            notifyDataSetChanged();
        }
    }

    public int[] mergeTabSizes(int[] headerSizes) {
        for (int i = 0; i < 4; i++) {
            if (headerSizes[i] > mTabSizes[i]) {
                mTabSizes[i] = headerSizes[i];
            }
        }
        this.mbSkip = true;
        notifyDataSetChanged();
        return this.mTabSizes;
    }

    private void updateSizes(TextView textView) {
        for (int i = 0; i < 4; i++) {
            mTabSizes[i] = 0;
        }
        this.mFieldsCount = 1;
        for (int i = 0; i < this.mRowsList.size(); i++) {
            String str = this.mRowsList.get(i);
            if (!this.mTablist) {
                float textWidth = Utils.getTextWidth(Utils.getColoredString(str).toString(), textView.getPaint())
                        + Utils.scale(this.mContext, 32.0f);
                if ((float) mTabSizes[0] < textWidth) {
                    mTabSizes[0] = (int) textWidth;
                }
            } else {
                String[] split = str.split("\t");
                int length = Math.min(4, split.length);
                if (this.mFieldsCount < length) {
                    this.mFieldsCount = length;
                }
                for (int i2 = 0; i2 < length; i2++) {
                    float textWidth2 = Utils.getTextWidth(Utils.getColoredString(split[i2]).toString(), textView.getPaint())
                            + Utils.scale(this.mContext, 32.0f);
                    if ((float) mTabSizes[i2] < textWidth2) {
                        mTabSizes[i2] = (int) textWidth2;
                    }
                }
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View inflate = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.layout_dialog_item, viewGroup, false);
        return new ViewHolder(inflate);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        int pos = viewHolder.getAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) pos = position;

        if (pos == 0 && !this.mbSkip) {
            updateSizes(viewHolder.mFields[0]);
        }
        for (int i2 = 0; i2 < 4; i2++) {
            if (this.mFieldsCount <= i2) {
                viewHolder.mFields[i2].setVisibility(View.GONE);
            } else {
                viewHolder.mFields[i2].setVisibility(View.VISIBLE);
                ViewGroup.LayoutParams lp = viewHolder.mFields[i2].getLayoutParams();
                lp.width = this.mTabSizes[i2];
                viewHolder.mFields[i2].setLayoutParams(lp);
                viewHolder.mFields[i2].setText("");
            }
        }
        String str = this.mRowsList.get(pos);
        if (!this.mTablist) {
            viewHolder.mFields[0].setText(Utils.getColoredString(str));
        } else {
            String[] split = str.split("\t");
            int length = Math.min(4, split.length);
            for (int i3 = 0; i3 < length; i3++) {
                viewHolder.mFields[i3].setText(Utils.getColoredString(split[i3]));
            }
        }
        if (this.mCurrentSelectedPosition == pos) {
            this.mCurrentSelectedView = viewHolder.mRowBg;
            viewHolder.mRowBg.setVisibility(View.VISIBLE);
            if (this.mOnClickListener != null) {
                this.mOnClickListener.onClick(pos, viewHolder.mFields[0].getText().toString());
            }
        } else {
            viewHolder.mRowBg.setVisibility(View.GONE);
        }

        ViewGroup.LayoutParams rootLp = viewHolder.getMView().getLayoutParams();
        rootLp.width = this.mNeedMatchParent ? -1 : -2;
        viewHolder.getMView().setLayoutParams(rootLp);

        viewHolder.getMView().setOnClickListener(view -> {
            int clickedPos = viewHolder.getAdapterPosition();
            if (clickedPos == RecyclerView.NO_POSITION) return;
            if (clickedPos != mCurrentSelectedPosition) {
                if (mCurrentSelectedView != null) {
                    mCurrentSelectedView.setVisibility(View.GONE);
                }
                mCurrentSelectedPosition = clickedPos;
                mCurrentSelectedView = viewHolder.mRowBg;
                viewHolder.mRowBg.setVisibility(View.VISIBLE);
                if (mOnClickListener != null) {
                    mOnClickListener.onClick(clickedPos, viewHolder.mFields[0].getText().toString());
                }
            } else if (mOnDoubleClickListener != null) {
                mOnDoubleClickListener.onDoubleClick();
            }
        });
    }

    @Override
    public int getItemCount() {
        return this.mRowsList.size();
    }
}
