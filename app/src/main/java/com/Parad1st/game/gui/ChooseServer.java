package com.byparad1st.game.gui;

import android.app.Activity;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.akexorcist.roundcornerprogressbar.RoundCornerProgressBar;
import com.byparad1st.game.R;
import com.byparad1st.game.gui.util.Utils;

public class ChooseServer {

    private final FrameLayout serverLayout;
    private final TextView percentText;
    private final RoundCornerProgressBar progressBar;

    public ChooseServer(Activity activity) {
        serverLayout = activity.findViewById(R.id.br_serverselect_layout);
        percentText = activity.findViewById(R.id.loading_percent);
        progressBar = activity.findViewById(R.id.progress_bar);
        Utils.HideLayout(serverLayout, false);
    }

    public void Update(int percent) {
        if (percent <= 100) {
            int clamped = Math.max(0, percent);
            if (percentText != null) {
                percentText.setText(clamped + "%");
            }
            if (progressBar != null) {
                progressBar.setProgress(clamped);
            }
        } else {
            Utils.HideLayout(serverLayout, true);
        }
    }

    public void Show() {
        Utils.ShowLayout(serverLayout, false);
    }
}
