package com.nvidia.devtech;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ryzn.game.R;

import java.util.ArrayList;

public class InputManager {

    public interface InputListener {
        void OnInputEnd(String str);
    }

    private LinearLayout mInputLayout = null;
    private EditText mInputEt = null;

    private View mButtonSlash = null;
    private View mButtonHistoryNext = null;
    private View mButtonHistoryPrev = null;
    private View mButtonOk = null;

    private Activity mContext = null;
    private boolean mIsShowing = false;

    private String mSavedInput = null;

    private final int mMaxHistory = 20;
    private ArrayList<String> mInputHistory = null;
    private int mCurrentHistoryMessage = 0;

    private Runnable mAnimTask = null;

    public boolean IsShowing() {
        return mIsShowing;
    }

    public InputManager(Activity act) {
        mContext = act;

        mInputLayout = mContext.findViewById(R.id.main_input);
        mInputEt = mContext.findViewById(R.id.main_et_input);
        mButtonSlash = mContext.findViewById(R.id.main_but_input_slash);
        mButtonHistoryPrev = mContext.findViewById(R.id.main_but_input_prev);
        mButtonHistoryNext = mContext.findViewById(R.id.main_but_input_next);
        mButtonOk = mContext.findViewById(R.id.main_but_input_ok);

        mInputHistory = new ArrayList<>();

        mButtonSlash.setOnClickListener(view -> {
            int pos = Math.max(0, mInputEt.getSelectionStart());
            mInputEt.getText().insert(pos, "/");
        });

        mButtonHistoryPrev.setOnClickListener(view -> {
            mCurrentHistoryMessage--;
            if (mCurrentHistoryMessage < 0) {
                mCurrentHistoryMessage = 0;
            }
            if (mCurrentHistoryMessage <= 0) {
                mInputEt.setText("");
                return;
            }
            mInputEt.setText(mInputHistory.get(mCurrentHistoryMessage - 1));
            mInputEt.setSelection(mInputEt.getText().length());
        });

        mButtonHistoryNext.setOnClickListener(view -> {
            mCurrentHistoryMessage++;
            if ((mCurrentHistoryMessage - 1) >= mInputHistory.size()) {
                mCurrentHistoryMessage--;
            }
            if (mCurrentHistoryMessage <= 0) {
                return;
            }
            mInputEt.setText(mInputHistory.get(mCurrentHistoryMessage - 1));
            mInputEt.setSelection(mInputEt.getText().length());
        });

        if (mButtonOk != null) {
            mButtonOk.setOnClickListener(view -> submitCurrentInput());
        }

        mInputEt.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE
                        || i == EditorInfo.IME_ACTION_SEND
                        || i == EditorInfo.IME_ACTION_NEXT
                        || (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN
                                && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    submitCurrentInput();
                    return true;
                }
                return false;
            }
        });

        HideInputLayout();
    }

    private void submitCurrentInput() {
        Editable editableText = mInputEt.getText();
        String str = editableText != null ? editableText.toString() : "";
        mInputEt.setText("");
        OnInputEnd(str);
    }

    public void onHeightChanged(int height) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mInputLayout.getLayoutParams();
        params.setMargins(0, 0, 0, height);
        mInputLayout.setLayoutParams(params);
    }

    public void ShowInputLayout() {
        mIsShowing = true;
        mInputLayout.setVisibility(View.INVISIBLE);

        mInputEt.requestFocus();
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mInputEt, InputMethodManager.SHOW_IMPLICIT);
        }

        if (mAnimTask != null) {
            mInputLayout.removeCallbacks(mAnimTask);
            mAnimTask = null;
        }

        mAnimTask = () -> {
            mInputLayout.setVisibility(View.VISIBLE);
            mInputEt.requestFocus();
        };
        mInputLayout.postDelayed(mAnimTask, 60);

        mCurrentHistoryMessage = 0;
        mSavedInput = null;
        if (mInputEt != null) {
            mInputEt.setText("");
        }
    }

    public void HideInputLayout() {
        mCurrentHistoryMessage = 0;
        if (mInputEt.getEditableText() != null) {
            mSavedInput = mInputEt.getEditableText().toString();
        }
        if (mAnimTask != null) {
            mInputLayout.removeCallbacks(mAnimTask);
            mAnimTask = null;
        }
        if (mContext.getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mContext.getCurrentFocus().getWindowToken(), 0);
            }
        }
        mInputLayout.setVisibility(View.GONE);
        mIsShowing = false;
    }

    private void OnInputEnd(String str) {
        if (!str.trim().isEmpty()) {
            if (mInputHistory.size() >= mMaxHistory) {
                mInputHistory.remove(mInputHistory.size() - 1);
            }
            mInputHistory.add(0, str);
        }
        InputListener listener = (InputListener) mContext;
        listener.OnInputEnd(str);
    }
}
