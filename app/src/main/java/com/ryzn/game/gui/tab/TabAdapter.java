package com.ryzn.game.gui.tab;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.ryzn.game.R;

import java.util.ArrayList;
import java.util.List;

public class TabAdapter extends RecyclerView.Adapter<TabAdapter.ViewHolder> implements Filterable {

    private List<PlayerData> mPlayerData;
    private final List<PlayerData> mPlayerDataCopy;
    private int selectedPosition = 0;
    private View selectedView;
    private OnRowActionListener rowActionListener;

    public interface OnRowActionListener {
        void onDoubleClick(PlayerData data);
    }

    public TabAdapter(List<PlayerData> playerData) {
        this.mPlayerData = playerData;
        this.mPlayerDataCopy = playerData;
    }

    public void setOnRowActionListener(OnRowActionListener listener) {
        this.rowActionListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.hassle_tab_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int pos = holder.getAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) pos = position;
        PlayerData data = this.mPlayerData.get(pos);

        holder.id.setText(String.valueOf(data.getId()));
        holder.name.setText(data.getName());
        holder.level.setText(String.valueOf(data.getLevel()));
        holder.ping.setText(String.valueOf(data.getPing()));

        if (selectedPosition == pos) {
            selectedView = holder.background;
            holder.background.setVisibility(View.VISIBLE);
        } else {
            holder.background.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(view -> {
            int clickedPos = holder.getAdapterPosition();
            if (clickedPos == RecyclerView.NO_POSITION) return;
            if (clickedPos != selectedPosition) {
                if (selectedView != null) {
                    selectedView.setVisibility(View.GONE);
                }
                selectedPosition = clickedPos;
                selectedView = holder.background;
                holder.background.setVisibility(View.VISIBLE);
            } else if (rowActionListener != null) {
                rowActionListener.onDoubleClick(data);
            }
        });
    }

    @Override
    public int getItemCount() {
        return this.mPlayerData.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String input = charSequence != null ? charSequence.toString().toLowerCase() : "";
                List<PlayerData> newList;
                if (input.isEmpty()) {
                    newList = mPlayerDataCopy;
                } else {
                    newList = new ArrayList<>();
                    for (PlayerData pd : mPlayerDataCopy) {
                        if (pd.getName() != null && pd.getName().toLowerCase().contains(input)) {
                            newList.add(pd);
                        }
                    }
                }
                FilterResults filterResults = new FilterResults();
                filterResults.values = newList;
                return filterResults;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                mPlayerData = (List<PlayerData>) filterResults.values;
                selectedPosition = 0;
                selectedView = null;
                notifyDataSetChanged();
            }
        };
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final ImageView background;
        public final TextView id;
        public final TextView level;
        public final ConstraintLayout rowRoot;
        public final TextView name;
        public final TextView ping;

        public ViewHolder(View itemView) {
            super(itemView);
            this.rowRoot = itemView.findViewById(R.id.hassle_tab_item);
            this.background = itemView.findViewById(R.id.row_bg);
            this.id = itemView.findViewById(R.id.player_id);
            this.name = itemView.findViewById(R.id.player_name);
            this.level = itemView.findViewById(R.id.player_level);
            this.ping = itemView.findViewById(R.id.player_ping);
        }
    }
}
