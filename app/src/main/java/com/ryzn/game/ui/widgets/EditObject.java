package com.ryzn.game.ui.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.ryzn.game.R;

public class EditObject {
    private static final int BUTTON_LEFT_RIGHT = 0;
    private static final int BUTTON_UP_DOWN = 1;
    private static final int BUTTON_PUSH_PULL = 2;
    private static final int BUTTON_SCALE = 3;
    private static final int BUTTON_ROT_X = 4;
    private static final int BUTTON_ROT_Y = 5;
    private static final int BUTTON_ROT_Z = 6;

    private int active_button = -1;

    private final Activity activity;
    private final ConstraintLayout attach_main_layot;
    private final MaterialButton attach_exit_butt;
    private final MaterialButton attach_save_butt;
    private final ConstraintLayout attach_leftright;
    private final ConstraintLayout attach_topbott;
    private final ConstraintLayout attach_pushpull;
    private final ConstraintLayout attach_scale;
    private final ConstraintLayout attach_rotX;
    private final ConstraintLayout attach_rotY;
    private final ConstraintLayout attach_rotZ;
    private final TextView attach_text_description;
    private final ConstraintLayout attach_left_butt;
    private final ConstraintLayout attach_right_button;

    public boolean visible = false;

    native void Click(int button, boolean state);

    native void Exit();

    native void Save();

    @SuppressLint("ClickableViewAccessibility")
    public EditObject(Activity activity) {
        this.activity = activity;
        attach_main_layot = (ConstraintLayout) activity.getLayoutInflater().inflate(R.layout.activity_game_editobject, null);
        activity.addContentView(attach_main_layot, new ConstraintLayout.LayoutParams(-1, -1));

        attach_left_butt = attach_main_layot.findViewById(R.id.attach_left_butt);
        attach_left_butt.setOnTouchListener(touchListener);

        attach_right_button = attach_main_layot.findViewById(R.id.attach_right_button);
        attach_right_button.setOnTouchListener(touchListener);

        attach_text_description = attach_main_layot.findViewById(R.id.attach_text_description);

        attach_save_butt = attach_main_layot.findViewById(R.id.attach_save_butt);
        attach_save_butt.setOnClickListener(view -> {
            Save();
            show(false);
        });

        attach_exit_butt = attach_main_layot.findViewById(R.id.attach_exit_butt);
        attach_exit_butt.setOnClickListener(view -> {
            Exit();
            show(false);
        });

        attach_leftright = attach_main_layot.findViewById(R.id.attach_leftright);
        attach_leftright.setOnClickListener(view -> {
            active_button = BUTTON_LEFT_RIGHT;
            attach_text_description.setText("Set Left / Right");
            selectItem(view);
        });

        attach_topbott = attach_main_layot.findViewById(R.id.attach_topbott);
        attach_topbott.setOnClickListener(view -> {
            active_button = BUTTON_UP_DOWN;
            attach_text_description.setText("Set Top / Bottom");
            selectItem(view);
        });

        attach_pushpull = attach_main_layot.findViewById(R.id.attach_pushpull);
        attach_pushpull.setOnClickListener(view -> {
            active_button = BUTTON_PUSH_PULL;
            attach_text_description.setText("Set Push / Pull");
            selectItem(view);
        });

        attach_scale = attach_main_layot.findViewById(R.id.attach_scale);
        attach_scale.setOnClickListener(view -> {
            attach_text_description.setText("Set Scale");
            active_button = BUTTON_SCALE;
            selectItem(view);
        });

        attach_rotX = attach_main_layot.findViewById(R.id.attach_rotX);
        attach_rotX.setOnClickListener(view -> {
            attach_text_description.setText("Set Rotation X");
            active_button = BUTTON_ROT_X;
            selectItem(view);
        });

        attach_rotY = attach_main_layot.findViewById(R.id.attach_rotY);
        attach_rotY.setOnClickListener(view -> {
            attach_text_description.setText("Set Rotation Y");
            active_button = BUTTON_ROT_Y;
            selectItem(view);
        });

        attach_rotZ = attach_main_layot.findViewById(R.id.attach_rotZ);
        attach_rotZ.setOnClickListener(view -> {
            attach_text_description.setText("Set Rotation Z");
            active_button = BUTTON_ROT_Z;
            selectItem(view);
        });

        show(false);
    }

    public void show(boolean z) {
        attach_main_layot.setVisibility(z ? View.VISIBLE : View.GONE);
        visible = z;
    }

    public void showWithoutReset(boolean z) {
        if (visible) {
            attach_main_layot.setVisibility(z ? View.VISIBLE : View.GONE);
        } else {
            attach_main_layot.setVisibility(View.GONE);
        }
    }

    private void selectItem(View view) {
        for (ConstraintLayout cl : new ConstraintLayout[]{
                attach_leftright, attach_topbott, attach_pushpull, attach_scale,
                attach_rotX, attach_rotY, attach_rotZ
        }) {
            cl.getChildAt(0).setBackgroundTintList(null);
            cl.getChildAt(2).setBackgroundTintList(null);
        }

        ConstraintLayout tmp = (ConstraintLayout) view;
        tmp.getChildAt(0).setBackgroundTintList(ColorStateList.valueOf(activity.getResources().getColor(R.color.ryzn_purple_primary)));
        tmp.getChildAt(2).setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#000000")));
    }

    private int fix_move = 0;
    private final int HOLD_INTERVAL = 80;
    private final Handler handler = new Handler();
    private Runnable holdRunnable;
    private boolean isHolding = false;

    private final View.OnTouchListener touchListener = new View.OnTouchListener() {
        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    fix_move = 10;
                    isHolding = true;
                    if (view == attach_left_butt) {
                        startHolding(false);
                    } else if (view == attach_right_button) {
                        startHolding(true);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    isHolding = false;
                    handler.removeCallbacks(holdRunnable);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (fix_move > 0) {
                        fix_move--;
                        if (fix_move > 0) return true;
                    }
                    break;
            }
            return true;
        }
    };

    private void startHolding(boolean isRight) {
        holdRunnable = new Runnable() {
            @Override
            public void run() {
                if (isHolding) {
                    Click(active_button, isRight);
                    handler.postDelayed(this, HOLD_INTERVAL);
                }
            }
        };
        handler.post(holdRunnable);
    }
}
