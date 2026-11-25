package com.ryzn.game.ui.widgets;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.ryzn.game.R;
import com.ryzn.game.ui.widgets.adapter.ScoreboardAdapter;

import java.util.ArrayList;
import java.util.List;

public class Scoreboard {
    private final ConstraintLayout scoreboard_layout;
    private final MaterialTextView scoreboard_server;
    private final MaterialTextView scoreboard_players;
    private final List<ScoreboardAdapter.PlayerData> playerData;
    public final ScoreboardAdapter scoreboardAdapter;

    public interface Listener {
        void _sendScoreboardClick(int player_id);
    }

    public interface DoubleClickListener {
        void onDoubleClick(int i);
    }

    public Scoreboard(Activity activity, Listener listener) {
        scoreboard_layout = (ConstraintLayout) activity.getLayoutInflater().inflate(R.layout.activity_game_scoreboard, null);
        activity.addContentView(scoreboard_layout, new ConstraintLayout.LayoutParams(-1, -1));

        scoreboard_server = scoreboard_layout.findViewById(R.id.scoreboard_server);
        scoreboard_players = scoreboard_layout.findViewById(R.id.scoreboard_players);
        RecyclerView scoreboard_players_list = scoreboard_layout.findViewById(R.id.scoreboard_players_list);
        scoreboard_players_list.setLayoutManager(new LinearLayoutManager(activity));

        playerData = new ArrayList<>();
        scoreboardAdapter = new ScoreboardAdapter(playerData);
        scoreboardAdapter.setOnDoubleClickListener(i -> {
            if (i >= 0 && i < playerData.size()) {
                listener._sendScoreboardClick(playerData.get(i).getId());
            } else {
                Log.e("Scoreboard", "Invalid index: " + i);
                listener._sendScoreboardClick(-1);
            }
        });
        scoreboard_players_list.setAdapter(scoreboardAdapter);

        show(false);
    }

    public void clearStats() {
        scoreboard_server.setText("Ryzn SA-MP");
        scoreboard_players.setText("Players: 0");
        playerData.clear();
        scoreboardAdapter.notifyDataSetChanged();
    }

    public void setStats(String server, int players) {
        scoreboard_server.setText(server);
        scoreboard_players.setText("Players: " + players);
    }

    public void addPlayer(int id, String name, int score, int ping, String color) {
        playerData.add(new ScoreboardAdapter.PlayerData(id, name, score, ping, color));
        scoreboardAdapter.notifyItemInserted(playerData.size() - 1);
    }

    public void show(boolean z) {
        if (!z) clearStats();
        scoreboard_layout.setVisibility(z ? View.VISIBLE : View.GONE);
    }
}
