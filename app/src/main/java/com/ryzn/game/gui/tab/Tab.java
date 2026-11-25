package com.ryzn.game.gui.tab;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.ryzn.game.R;
import com.ryzn.game.gui.util.LinearLayoutManagerWrapper;
import com.ryzn.game.gui.util.Utils;
import com.nvidia.devtech.NvEventQueueActivity;

import java.util.ArrayList;
import java.util.List;

public class Tab {
    public final Activity aactivity;
    public final ConstraintLayout mInputLayout;
    public final TextView mOnline;
    public final List<PlayerData> mPlayerData = new ArrayList<>();
    public final RecyclerView mPlayersList;
    private TabAdapter mTabAdapter;

    public Tab(Activity activity) {
        this.aactivity = activity;
        this.mInputLayout = activity.findViewById(R.id.hassle_tab_main);
        this.mOnline = activity.findViewById(R.id.players_list_value_players);
        this.mPlayersList = activity.findViewById(R.id.players_list);
        this.mTabAdapter = new TabAdapter(this.mPlayerData);

        if (this.mInputLayout != null) {
            this.mInputLayout.setOnClickListener(view -> close(activity, view));
        }
        Utils.HideLayout(this.mInputLayout, false);
    }

    public boolean isShowing() {
        return mInputLayout != null && mInputLayout.getVisibility() == View.VISIBLE;
    }

    public void close(Activity activity, View view) {
        if (mInputLayout != null) {
            Utils.HideLayout(mInputLayout, false);
        }
        NvEventQueueActivity act = NvEventQueueActivity.getInstance();
        if (act != null) {
            act.togglePlayer(0);
        }
        if (mPlayersList != null) {
            mPlayersList.getRecycledViewPool().clear();
        }
        this.mPlayerData.clear();
        if (this.mTabAdapter != null) {
            this.mTabAdapter.notifyDataSetChanged();
        }
    }

    public void clearStat() {
        this.mPlayerData.clear();
    }

    public void setStat(Integer id, String name, Integer score, Integer ping) {
        if (id != null && id < 0) {
            clearStat();
            return;
        }
        this.mPlayerData.add(new PlayerData(
                id != null ? id : 0,
                name != null ? name : "",
                score != null ? score : 0,
                ping != null ? ping : 0
        ));
    }

    public void show(boolean isAnim) {
        if (mOnline != null) {
            mOnline.setText("Pemain: " + this.mPlayerData.size());
        }
        mTabAdapter = new TabAdapter(this.mPlayerData);
        mTabAdapter.setOnRowActionListener(data -> close(aactivity, mInputLayout));
        mPlayersList.setLayoutManager(new LinearLayoutManagerWrapper(NvEventQueueActivity.getInstance()));
        mPlayersList.setAdapter(this.mTabAdapter);
        Utils.ShowLayout(this.mInputLayout, isAnim);
    }

    public ConstraintLayout getInputLayout() {
        return this.mInputLayout;
    }
}
