package com.ryzn.game.gui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ryzn.game.R;
import com.ryzn.game.gui.util.Utils;
import com.nvidia.devtech.NvEventQueueActivity;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class BrDialogWindow {
    public static final int DIALOG_STYLE_MSGBOX = 0;
    public static final int DIALOG_STYLE_INPUT = 1;
    public static final int DIALOG_STYLE_LIST = 2;
    public static final int DIALOG_STYLE_PASSWORD = 3;
    public static final int DIALOG_STYLE_TABLIST = 4;
    public static final int DIALOG_STYLE_TABLIST_HEADERS = 5;

    private final Activity activity;
    private final ConstraintLayout mMainLayout;
    private final View mDialogBody;
    private final View mDialogTextLayout;
    private final View mDialogInputLayout;
    private final View mDialogListLayout;
    private final View mDialogTabListRow;
    private final View mDialogList;
    private final TextView mDialogCaption;
    private final TextView mDialogText;
    private final EditText mDialogInput;
    private final TextView[] mDialogTabField = new TextView[4];
    private final RecyclerView mDialogListRecycler;
    private final View mDialogButtonPositive;
    private final View mDialogButtonNegative;
    private final TextView mDialogButtonPositiveText;
    private final TextView mDialogButtonNegativeText;
    private final PcScrollbarView mDialogTextScrollbar;
    private final PcScrollbarView mDialogListScrollbar;
    private final SampGuiSkin mSkin = new SampGuiSkin();
    private boolean mSkinApplied = false;

    private DialogAdapter mListAdapter;
    private final ArrayList<String> mRowsList = new ArrayList<>();
    private final int[] mTabSizes = new int[]{0, 0, 0, 0};

    private int mCurrentDialogId = 0;
    private int mDialogStyle = 0;
    private String mCaption = "";
    private String mText = "";
    private String mButtonPositive = "";
    private String mButtonNegative = "";
    private int mCurrentListitem = -1;
    private String mCurrentInputtext = "";
    public boolean isShow = false;

    public BrDialogWindow(Activity activity) {
        this.activity = activity;
        this.mMainLayout = activity.findViewById(R.id.dw_root);
        this.mDialogBody = activity.findViewById(R.id.dialog_body);
        this.mDialogTextLayout = activity.findViewById(R.id.dialog_text_layout);
        this.mDialogInputLayout = activity.findViewById(R.id.dialog_input_layout);
        this.mDialogListLayout = activity.findViewById(R.id.dialog_list_layout);
        this.mDialogTabListRow = activity.findViewById(R.id.dialog_tablist_row);
        this.mDialogList = activity.findViewById(R.id.dialog_list);
        this.mDialogCaption = activity.findViewById(R.id.dialog_caption);
        this.mDialogText = activity.findViewById(R.id.dialog_text);
        this.mDialogInput = activity.findViewById(R.id.dialog_input);
        this.mDialogTabField[0] = activity.findViewById(R.id.dialog_field1);
        this.mDialogTabField[1] = activity.findViewById(R.id.dialog_field2);
        this.mDialogTabField[2] = activity.findViewById(R.id.dialog_field3);
        this.mDialogTabField[3] = activity.findViewById(R.id.dialog_field4);
        this.mDialogListRecycler = activity.findViewById(R.id.dialog_list_recycler);
        this.mDialogButtonPositive = activity.findViewById(R.id.button_positive);
        this.mDialogButtonNegative = activity.findViewById(R.id.button_negative);
        this.mDialogButtonPositiveText = activity.findViewById(R.id.button_positive_text);
        this.mDialogButtonNegativeText = activity.findViewById(R.id.button_negative_text);
        this.mDialogTextScrollbar = activity.findViewById(R.id.dialog_text_scrollbar);
        this.mDialogListScrollbar = activity.findViewById(R.id.dialog_list_scrollbar);

        mDialogInput.setOnClickListener(view -> {
            mDialogInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(mDialogInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        mDialogInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mCurrentInputtext = s != null ? s.toString() : "";
            }
        });
        mDialogInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(mDialogInput.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });

        Utils.scaleViewAndChildren(activity, mMainLayout);
        if (mDialogTextScrollbar != null && mDialogTextLayout instanceof ScrollView) {
            mDialogTextScrollbar.attachToScrollView((ScrollView) mDialogTextLayout);
        }
        if (mDialogListScrollbar != null) {
            mDialogListScrollbar.attachToRecyclerView(mDialogListRecycler);
        }
        applyPcSkin();
        close();
    }

    private void applyPcSkin() {
        boolean changed = mSkin.reloadIfNeeded(activity);
        if (!changed && mSkinApplied) {
            return;
        }
        mSkinApplied = true;
        if (mDialogTextScrollbar != null) {
            mDialogTextScrollbar.setSkin(mSkin);
        }
        if (mDialogListScrollbar != null) {
            mDialogListScrollbar.setSkin(mSkin);
        }
        Drawable btnPosBg = mSkin.createButtonDrawable(activity);
        if (btnPosBg != null) {
            mDialogButtonPositive.setBackground(btnPosBg);
        }
        Drawable btnNegBg = mSkin.createButtonDrawable(activity);
        if (btnNegBg != null) {
            mDialogButtonNegative.setBackground(btnNegBg);
        }
        ColorStateList btnTextColors = mSkin.createButtonTextColors();
        mDialogButtonPositiveText.setTextColor(btnTextColors);
        mDialogButtonNegativeText.setTextColor(btnTextColors);

        Drawable editBg = mSkin.createEditBoxDrawable(activity);
        if (editBg != null) {
            mDialogInputLayout.setBackground(editBg);
        }
        Drawable listBg = mSkin.createListBoxDrawable(activity);
        if (listBg != null) {
            mDialogList.setBackground(listBg);
        }
        if (mListAdapter != null) {
            mListAdapter.setSkin(mSkin);
        }
    }

    public void show(int dialogId, int dialogStyle, String title, String message, String button1, String button2) {
        isShow = true;
        mCurrentDialogId = dialogId;
        mDialogStyle = dialogStyle;
        mCaption = title != null ? title : "";
        mText = message != null ? message : "";
        mButtonPositive = button1 != null ? button1 : "";
        mButtonNegative = button2 != null ? button2 : "";

        ConstraintLayout.LayoutParams textLp = (ConstraintLayout.LayoutParams) mDialogTextLayout.getLayoutParams();
        textLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mDialogTextLayout.setLayoutParams(textLp);

        ViewGroup.LayoutParams rootLp = mMainLayout.getLayoutParams();
        if (rootLp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) rootLp).gravity = 17;
            mMainLayout.setLayoutParams(rootLp);
        }

        applyPcSkin();
        loadDialog();
        Utils.ShowLayout(mMainLayout, false);
    }

    public void close() {
        isShow = false;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && mDialogInput != null) {
            imm.hideSoftInputFromWindow(mDialogInput.getWindowToken(), 0);
        }
        Utils.HideLayout(mMainLayout, false);
    }

    public void onHeightChanged(int height) {
        if (mMainLayout.getVisibility() != View.VISIBLE) {
            return;
        }
        if (mDialogStyle != DIALOG_STYLE_INPUT && mDialogStyle != DIALOG_STYLE_PASSWORD) {
            return;
        }
        ConstraintLayout.LayoutParams textLp = (ConstraintLayout.LayoutParams) mDialogTextLayout.getLayoutParams();
        ViewGroup.LayoutParams rootLp = mMainLayout.getLayoutParams();
        if (height <= 150) {
            textLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mDialogTextLayout.setLayoutParams(textLp);
            if (rootLp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) rootLp).gravity = 17;
                mMainLayout.setLayoutParams(rootLp);
            }
        } else {
            textLp.height = (int) Utils.scale(activity, 104.0f);
            mDialogTextLayout.setLayoutParams(textLp);
            if (rootLp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) rootLp).gravity = 49;
                mMainLayout.setLayoutParams(rootLp);
            }
        }
        if (mDialogTextScrollbar != null) {
            mDialogTextLayout.post(mDialogTextScrollbar::syncFromTarget);
        }
    }

    public void sendDialogResponse(int buttonId, int listItem, String inputText) {
        int resolvedListItem = listItem;
        if (resolvedListItem == -1 && (mDialogStyle == DIALOG_STYLE_LIST
                || mDialogStyle == DIALOG_STYLE_TABLIST
                || mDialogStyle == DIALOG_STYLE_TABLIST_HEADERS)) {
            resolvedListItem = 0;
        }
        String finalText = inputText != null ? inputText : "";
        if (mDialogStyle == DIALOG_STYLE_INPUT || mDialogStyle == DIALOG_STYLE_PASSWORD) {
            finalText = mDialogInput.getText() != null ? mDialogInput.getText().toString() : finalText;
        }

        byte[] payload;
        try {
            payload = finalText.getBytes("windows-1251");
        } catch (UnsupportedEncodingException e) {
            payload = finalText.getBytes();
        }

        NvEventQueueActivity act = NvEventQueueActivity.getInstance();
        if (act != null) {
            // CJavaWrapper expects: sendDialogResponse(buttonId, dialogId, listItem, inputBytes)
            act.sendDialogResponse(buttonId, mCurrentDialogId, resolvedListItem, payload);
        }
        close();
    }

    private void loadDialog() {
        clearDialogData();
        loadDialogData();
        loadButtons();
        loadRecycler();
        loadSizes();
    }

    private void clearDialogData() {
        mRowsList.clear();
        mDialogInput.setText("");
        mCurrentListitem = -1;
        mCurrentInputtext = "";
        for (int i = 0; i < 4; i++) {
            mTabSizes[i] = 0;
        }
    }

    private void loadDialogData() {
        setDialogStyle(mDialogStyle);
        mDialogCaption.setText(Utils.getColoredString(mCaption));
        if (mDialogStyle == DIALOG_STYLE_MSGBOX
                || mDialogStyle == DIALOG_STYLE_INPUT
                || mDialogStyle == DIALOG_STYLE_PASSWORD) {
            mDialogText.setText(Utils.getColoredString(mText));
            return;
        }

        if (mDialogStyle != DIALOG_STYLE_LIST) {
            StringBuilder sb = new StringBuilder(mText);
            for (int i = 0; i < sb.length(); i++) {
                if (sb.charAt(i) == '\t') {
                    while (i + 1 < sb.length() && sb.charAt(i + 1) == '\t') {
                        sb.deleteCharAt(i + 1);
                    }
                }
            }
            mText = sb.toString();
        }

        mCurrentListitem = 0;
        String[] split = mText.split("\n");
        for (int i = 0; i < split.length; i++) {
            if (i == 0 && mDialogStyle == DIALOG_STYLE_TABLIST_HEADERS) {
                String[] cols = split[i].split("\t");
                int length = Math.min(4, cols.length);
                for (int col = 0; col < 4; col++) {
                    if (length <= col) {
                        mDialogTabField[col].setVisibility(View.GONE);
                    } else {
                        mDialogTabField[col].setVisibility(View.VISIBLE);
                        mTabSizes[col] = Utils.getTextWidth(Utils.getColoredString(cols[col]).toString(), mDialogTabField[col].getPaint())
                                + (int) Utils.scale(activity, 32.0f);
                        mDialogTabField[col].setText(Utils.getColoredString(cols[col]));
                    }
                }
            } else {
                if ((i == 1 && mDialogStyle == DIALOG_STYLE_TABLIST_HEADERS)
                        || (i == 0 && mDialogStyle != DIALOG_STYLE_TABLIST_HEADERS)) {
                    String plain = Utils.getStringWithoutColors(split[i]);
                    if (mDialogStyle == DIALOG_STYLE_TABLIST_HEADERS || mDialogStyle == DIALOG_STYLE_TABLIST) {
                        plain = plain.split("\t")[0];
                    }
                    mCurrentInputtext = plain;
                }
                mRowsList.add(split[i]);
            }
        }
    }

    private void loadSizes() {
        ConstraintLayout.LayoutParams lp1 = (ConstraintLayout.LayoutParams) mDialogListLayout.getLayoutParams();
        lp1.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mDialogListLayout.setLayoutParams(lp1);

        ConstraintLayout.LayoutParams lp2 = (ConstraintLayout.LayoutParams) mDialogList.getLayoutParams();
        lp2.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mDialogList.setLayoutParams(lp2);

        ConstraintLayout.LayoutParams lp3 = (ConstraintLayout.LayoutParams) mDialogListRecycler.getLayoutParams();
        lp3.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mDialogListRecycler.setLayoutParams(lp3);

        mDialogBody.post(() -> {
            if (mListAdapter == null) return;
            if (mDialogBody.getWidth() > mDialogList.getWidth()) {
                if (mDialogBody.getWidth() > mDialogListLayout.getWidth()) {
                    ConstraintLayout.LayoutParams a1 = (ConstraintLayout.LayoutParams) mDialogListLayout.getLayoutParams();
                    a1.width = 0;
                    mDialogListLayout.setLayoutParams(a1);
                }
                ConstraintLayout.LayoutParams a2 = (ConstraintLayout.LayoutParams) mDialogList.getLayoutParams();
                a2.width = 0;
                mDialogList.setLayoutParams(a2);

                ConstraintLayout.LayoutParams a3 = (ConstraintLayout.LayoutParams) mDialogListRecycler.getLayoutParams();
                a3.width = 0;
                mDialogListRecycler.setLayoutParams(a3);

                mListAdapter.setMatchParent(true);
            } else {
                mListAdapter.setMatchParent(false);
            }
            if (mDialogStyle == DIALOG_STYLE_TABLIST_HEADERS) {
                int[] merged = mListAdapter.mergeTabSizes(mTabSizes);
                for (int i = 0; i < 4; i++) {
                    ViewGroup.LayoutParams lpCol = mDialogTabField[i].getLayoutParams();
                    lpCol.width = merged[i];
                    mDialogTabField[i].setLayoutParams(lpCol);
                }
            }
            if (mDialogListScrollbar != null) {
                mDialogListScrollbar.syncFromTarget();
            }
        });
        if (mDialogTextScrollbar != null) {
            mDialogTextLayout.post(mDialogTextScrollbar::syncFromTarget);
        }
        if (mDialogListScrollbar != null) {
            mDialogListRecycler.post(mDialogListScrollbar::syncFromTarget);
        }
    }

    private void loadButtons() {
        mDialogButtonPositive.setVisibility(View.VISIBLE);
        mDialogButtonNegative.setVisibility(View.VISIBLE);
        mDialogButtonPositiveText.setText(Utils.getColoredString(mButtonPositive));
        mDialogButtonNegativeText.setText(Utils.getColoredString(mButtonNegative));
        mDialogButtonPositive.setOnClickListener(view -> sendDialogResponse(1, mCurrentListitem, mCurrentInputtext));
        if (mButtonNegative.isEmpty()) {
            mDialogButtonNegative.setVisibility(View.GONE);
        } else {
            mDialogButtonNegative.setOnClickListener(view -> sendDialogResponse(0, mCurrentListitem, mCurrentInputtext));
        }
    }

    private void setDialogStyle(int style) {
        if (style == DIALOG_STYLE_MSGBOX) {
            mDialogTextLayout.setVisibility(View.VISIBLE);
            mDialogInputLayout.setVisibility(View.GONE);
            mDialogListLayout.setVisibility(View.GONE);
            if (mDialogListScrollbar != null) mDialogListScrollbar.setVisibility(View.GONE);
        } else if (style == DIALOG_STYLE_INPUT) {
            mDialogTextLayout.setVisibility(View.VISIBLE);
            mDialogInputLayout.setVisibility(View.VISIBLE);
            mDialogInput.setInputType(524353);
            mDialogListLayout.setVisibility(View.GONE);
            if (mDialogListScrollbar != null) mDialogListScrollbar.setVisibility(View.GONE);
        } else if (style == DIALOG_STYLE_LIST) {
            mDialogTextLayout.setVisibility(View.GONE);
            if (mDialogTextScrollbar != null) mDialogTextScrollbar.setVisibility(View.GONE);
            mDialogInputLayout.setVisibility(View.GONE);
            mDialogListLayout.setVisibility(View.VISIBLE);
            mDialogTabListRow.setVisibility(View.GONE);
        } else if (style == DIALOG_STYLE_PASSWORD) {
            mDialogTextLayout.setVisibility(View.VISIBLE);
            mDialogInputLayout.setVisibility(View.VISIBLE);
            mDialogInput.setInputType(524417);
            mDialogListLayout.setVisibility(View.GONE);
            if (mDialogListScrollbar != null) mDialogListScrollbar.setVisibility(View.GONE);
        } else if (style == DIALOG_STYLE_TABLIST) {
            mDialogTextLayout.setVisibility(View.GONE);
            if (mDialogTextScrollbar != null) mDialogTextScrollbar.setVisibility(View.GONE);
            mDialogInputLayout.setVisibility(View.GONE);
            mDialogListLayout.setVisibility(View.VISIBLE);
            mDialogTabListRow.setVisibility(View.GONE);
        } else if (style == DIALOG_STYLE_TABLIST_HEADERS) {
            mDialogTextLayout.setVisibility(View.GONE);
            if (mDialogTextScrollbar != null) mDialogTextScrollbar.setVisibility(View.GONE);
            mDialogInputLayout.setVisibility(View.GONE);
            mDialogListLayout.setVisibility(View.VISIBLE);
            mDialogTabListRow.setVisibility(View.VISIBLE);
        }
    }

    private void loadRecycler() {
        mDialogListRecycler.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.VERTICAL, false));
        mListAdapter = new DialogAdapter(mRowsList, activity,
                mDialogStyle == DIALOG_STYLE_TABLIST || mDialogStyle == DIALOG_STYLE_TABLIST_HEADERS);
        mListAdapter.setSkin(mSkin);
        mDialogListRecycler.setAdapter(mListAdapter);
        mListAdapter.setOnClickListener((pos, str) -> {
            if (pos != -1) {
                mCurrentListitem = pos;
                mCurrentInputtext = str;
            }
        });
        mListAdapter.setOnDoubleClickListener(() -> sendDialogResponse(1, mCurrentListitem, mCurrentInputtext));
    }
}
