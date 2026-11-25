package com.ryzn.launcher.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ryzn.game.R;
import com.ryzn.launcher.model.ServerInfo;
import com.ryzn.launcher.other.MangaUiHelper;

import java.util.List;

/**
 * RecyclerView adapter displaying SA-MP servers with live player counts, ping, status dot,
 * IP:Port, and selection highlight in Bright White Modern Manga theme.
 */
public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ViewHolder> {

    public interface OnServerClickListener {
        void onServerClick(ServerInfo server, int position);
    }

    public interface OnServerLongClickListener {
        void onServerLongClick(ServerInfo server, int position);
    }

    private final List<ServerInfo> servers;
    private int selectedPosition = 0;
    private OnServerClickListener clickListener;
    private OnServerLongClickListener longClickListener;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View root;
        final ImageView iconNode;
        final ImageView iconLock;
        final TextView serverName;
        final TextView serverSubtitle;
        final TextView playersText;
        final View statusDot;
        final TextView pingText;
        final ImageView signalIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.item_server_root);
            iconNode = itemView.findViewById(R.id.item_server_icon);
            iconLock = itemView.findViewById(R.id.item_lock_icon);
            serverName = itemView.findViewById(R.id.item_server_name);
            serverSubtitle = itemView.findViewById(R.id.item_server_subtitle);
            playersText = itemView.findViewById(R.id.item_players);
            statusDot = itemView.findViewById(R.id.item_status_dot);
            pingText = itemView.findViewById(R.id.item_ping);
            signalIcon = itemView.findViewById(R.id.item_signal_icon);
        }
    }

    public ServerAdapter(List<ServerInfo> servers) {
        this.servers = servers;
    }

    public void setOnServerClickListener(OnServerClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnServerLongClickListener(OnServerLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setSelectedPosition(int position) {
        if (servers.isEmpty()) {
            selectedPosition = -1;
            return;
        }
        int clamped = Math.max(0, Math.min(position, servers.size() - 1));
        int prev = selectedPosition;
        selectedPosition = clamped;
        if (prev >= 0 && prev < servers.size()) {
            notifyItemChanged(prev);
        }
        if (selectedPosition >= 0 && selectedPosition < servers.size()) {
            notifyItemChanged(selectedPosition);
        }
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public ServerInfo getSelectedServer() {
        if (selectedPosition >= 0 && selectedPosition < servers.size()) {
            return servers.get(selectedPosition);
        }
        return servers.isEmpty() ? null : servers.get(0);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_server, parent, false);
        ViewHolder holder = new ViewHolder(view);
        MangaUiHelper.attachPressFeedback(holder.root);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < 0 || position >= servers.size()) return;
        final ServerInfo server = servers.get(position);
        boolean isSelected = position == selectedPosition;

        holder.root.setBackgroundResource(isSelected
                ? R.drawable.bg_server_item_selected
                : R.drawable.bg_server_item_normal);

        holder.serverName.setText(server.name);
        holder.serverSubtitle.setText(server.getAddress() + " • " + server.mode);
        holder.iconLock.setVisibility(server.hasPassword ? View.VISIBLE : View.GONE);
        holder.playersText.setText(server.getPlayersFormatted());

        if (server.querying) {
            holder.statusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
            holder.pingText.setText("Cek...");
            holder.pingText.setTextColor(ContextCompat.getColor(holder.root.getContext(), R.color.ryzn_text_secondary));
            holder.signalIcon.setImageResource(R.drawable.ic_signal_off);
        } else if (server.online) {
            holder.statusDot.setBackgroundResource(R.drawable.bg_status_dot_online);
            holder.pingText.setText(server.getPingFormatted());
            holder.pingText.setTextColor(ContextCompat.getColor(holder.root.getContext(), R.color.ryzn_status_online));
            holder.signalIcon.setImageResource(R.drawable.ic_signal_good);
        } else {
            holder.statusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
            holder.pingText.setText("Offline");
            holder.pingText.setTextColor(ContextCompat.getColor(holder.root.getContext(), R.color.ryzn_text_muted));
            holder.signalIcon.setImageResource(R.drawable.ic_signal_off);
        }

        holder.root.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            setSelectedPosition(pos);
            if (clickListener != null) {
                clickListener.onServerClick(servers.get(pos), pos);
            }
        });

        holder.root.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            if (longClickListener != null) {
                longClickListener.onServerLongClick(servers.get(pos), pos);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return servers.size();
    }
}
