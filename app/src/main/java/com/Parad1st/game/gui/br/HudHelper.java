package com.byparad1st.game.gui.br;

import android.app.Activity;

import android.text.Html;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.view.View;

import com.byparad1st.game.R;

public class HudHelper {
    public Activity activity;
    public TextView helpertext;
    public ConstraintLayout main_layout;

    public HudHelper(Activity aactivity) {
        activity = aactivity;
        main_layout = aactivity.findViewById(R.id.helper_main_layout);
        main_layout.setVisibility(View.GONE);
        helpertext = aactivity.findViewById(R.id.hint_text_instructor);
        helpertext.setOnClickListener( view -> {
            main_layout.setVisibility(View.GONE);
            helpertext.setText("");
        });
    }

    public void ShowHelper(int type)
    {
        if (main_layout != null) {
            main_layout.setVisibility(View.GONE);
        }
    }

    public void HideHelper()
    {
        if (main_layout != null) {
            main_layout.setVisibility(View.GONE);
        }
        if (helpertext != null) {
            helpertext.setText("");
        }
    }
}
