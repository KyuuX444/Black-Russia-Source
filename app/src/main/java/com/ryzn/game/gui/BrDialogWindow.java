package com.ryzn.game.gui;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ryzn.game.R;
import com.ryzn.game.gui.util.Utils;

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
    private final LinearLayout mDialogContainer;
    private final LinearLayout mDialogBody;
    private final View mDialogTextLayout;
    private final ScrollView mDialogTextScroll;
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

    private DialogAdapter mListAdapter;
    private final ArrayList<String[]> mParsedRows = new ArrayList<>();
    private final int[] mTabSizes = new int[]{0, 0, 0, 0};
    private int mColumnCount = 1;

    private int mCurrentDialogId = -1;
    private int mDialogStyle = -1;
    private String mCaption = "";
    private String mText = "";
    private String mButtonPositive = "";
    private String mButtonNegative = "";
    private int mCurrentListitem = -1;
    private String mCurrentInputtext = "";

    public interface ResponseListener {
        void onDialogResponse(int dialogId, int buttonId, int listItem, byte[] input);
    }

    private ResponseListener mResponseListener;
    public boolean isShow = false;

    public BrDialogWindow(Activity activity) {
        this.activity = activity;
        View rootView = activity.findViewById(R.id.dw_root);
        if (rootView == null) {
            rootView = activity.getLayoutInflater().inflate(R.layout.window_dialog, null);
            activity.addContentView(rootView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        this.mMainLayout = (ConstraintLayout) rootView;
        this.mDialogContainer = rootView.findViewById(R.id.dialog);
        this.mDialogBody = rootView.findViewById(R.id.dialog_body);
        this.mDialogTextLayout = rootView.findViewById(R.id.dialog_text_layout);
        this.mDialogTextScroll = rootView.findViewById(R.id.dialog_text_scroll);
        this.mDialogInputLayout = rootView.findViewById(R.id.dialog_input_layout);
        this.mDialogListLayout = rootView.findViewById(R.id.dialog_list_layout);
        this.mDialogTabListRow = rootView.findViewById(R.id.dialog_tablist_row);
        this.mDialogList = rootView.findViewById(R.id.dialog_list);
        this.mDialogCaption = rootView.findViewById(R.id.dialog_caption);
        this.mDialogText = rootView.findViewById(R.id.dialog_text);
        this.mDialogInput = rootView.findViewById(R.id.dialog_input);
        this.mDialogTabField[0] = rootView.findViewById(R.id.dialog_field1);
        this.mDialogTabField[1] = rootView.findViewById(R.id.dialog_field2);
        this.mDialogTabField[2] = rootView.findViewById(R.id.dialog_field3);
        this.mDialogTabField[3] = rootView.findViewById(R.id.dialog_field4);
        this.mDialogListRecycler = rootView.findViewById(R.id.dialog_list_recycler);
        this.mDialogButtonPositive = rootView.findViewById(R.id.button_positive);
        this.mDialogButtonNegative = rootView.findViewById(R.id.button_negative);
        this.mDialogButtonPositiveText = rootView.findViewById(R.id.button_positive_text);
        this.mDialogButtonNegativeText = rootView.findViewById(R.id.button_negative_text);

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

        mDialogListRecycler.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.VERTICAL, false));
        mDialogListRecycler.setItemAnimator(null);
        close();
    }

    public void show(int dialogId, int dialogStyle, String title, String message, String button1, String button2) {
        isShow = true;
        mCurrentDialogId = dialogId;
        mDialogStyle = dialogStyle;
        mCaption = title != null ? title : "";
        mText = message != null ? message : "";
        mButtonPositive = button1 != null ? button1 : "";
        mButtonNegative = button2 != null ? button2 : "";

        resetKeyboardOffset();
        loadDialog();
        mMainLayout.setAlpha(1.0f);
        mMainLayout.setVisibility(View.VISIBLE);
    }

    public void setResponseListener(ResponseListener listener) {
        this.mResponseListener = listener;
    }

    public void showWithoutReset(boolean visible) {
        if (isShow && mCurrentDialogId != -1) {
            mMainLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        } else {
            mMainLayout.setVisibility(View.GONE);
        }
    }

    public void close() {
        isShow = false;
        resetKeyboardOffset();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && mDialogInput != null) {
            imm.hideSoftInputFromWindow(mDialogInput.getWindowToken(), 0);
        }
        mMainLayout.setVisibility(View.GONE);
    }

    private void resetKeyboardOffset() {
        if (mDialogContainer != null && mDialogContainer.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) mDialogContainer.getLayoutParams();
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0;
                lp.verticalBias = 0.5f;
                mDialogContainer.setLayoutParams(lp);
            }
        }
    }

    public void onHeightChanged(int height) {
        if (mMainLayout.getVisibility() != View.VISIBLE) {
            return;
        }
        if (mDialogStyle != DIALOG_STYLE_INPUT && mDialogStyle != DIALOG_STYLE_PASSWORD) {
            return;
        }
        if (!(mDialogContainer.getLayoutParams() instanceof ConstraintLayout.LayoutParams)) {
            return;
        }
        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) mDialogContainer.getLayoutParams();
        if (height <= 120) {
            lp.bottomMargin = 0;
            lp.verticalBias = 0.5f;
        } else {
            lp.bottomMargin = height;
            lp.verticalBias = 1.0f;
        }
        mDialogContainer.setLayoutParams(lp);
    }

    public void sendDialogResponse(int buttonId, int listItem, String inputText) {
        int resolvedListItem = listItem;
        if (resolvedListItem == -1 && isListStyle(mDialogStyle)) {
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

        if (mResponseListener != null) {
            mResponseListener.onDialogResponse(mCurrentDialogId, buttonId, resolvedListItem, payload);
        }
        close();
    }

    private static boolean isListStyle(int style) {
        return style == DIALOG_STYLE_LIST
                || style == DIALOG_STYLE_TABLIST
                || style == DIALOG_STYLE_TABLIST_HEADERS;
    }

    private void loadDialog() {
        clearDialogData();
        loadDialogData();
        loadButtons();
        if (isListStyle(mDialogStyle)) {
            loadRecycler();
            applyAlignedSizes();
        }
    }

    private void clearDialogData() {
        mParsedRows.clear();
        mListAdapter = null;
        mDialogInput.setText("");
        mCurrentListitem = -1;
        mCurrentInputtext = "";
        mColumnCount = 1;
        for (int i = 0; i < 4; i++) {
            mTabSizes[i] = 0;
            mDialogTabField[i].setVisibility(View.GONE);
            mDialogTabField[i].setText("");
        }
    }

    private static String[] parseTabColumns(String line, int maxHintCols, boolean collapseMultiTabs) {
        if (line == null) return new String[]{""};
        String normalized = line;
        if (collapseMultiTabs) {
            normalized = line.replaceAll("\t+", "\t");
        } else {
            String[] raw = line.split("\t", -1);
            if (raw.length > Math.max(4, maxHintCols)) {
                normalized = line.replaceAll("\t+", "\t");
            }
        }
        String[] parts = normalized.split("\t", -1);
        if (parts.length <= 4) {
            return parts;
        }
        String[] capped = new String[4];
        capped[0] = parts[0];
        capped[1] = parts[1];
        capped[2] = parts[2];
        StringBuilder rest = new StringBuilder(parts[3]);
        for (int i = 4; i < parts.length; i++) {
            rest.append(' ').append(parts[i]);
        }
        capped[3] = rest.toString();
        return capped;
    }

    private void loadDialogData() {
        setDialogStyle(mDialogStyle);
        mDialogCaption.setText(Utils.getColoredString(mCaption));
        if (!isListStyle(mDialogStyle)) {
            mDialogText.setText(Utils.getColoredString(mText));
            if (mDialogTextScroll != null) {
                mDialogTextScroll.scrollTo(0, 0);
            }
            return;
        }

        mCurrentListitem = 0;
        String[] lines = mText.split("\n", -1);
        TextPaint paint = mDialogTabField[0].getPaint();
        int colPadEnd = mDialogTabField[0].getPaddingEnd();
        int lastColPadEnd = mDialogTabField[3].getPaddingEnd();

        String[] headerCols = new String[0];
        int startRow = 0;
        if (mDialogStyle == DIALOG_STYLE_TABLIST_HEADERS && lines.length > 0) {
            headerCols = parseTabColumns(lines[0], 4, false);
            mColumnCount = Math.max(1, Math.min(4, headerCols.length));
            startRow = 1;
        }

        if (mDialogStyle == DIALOG_STYLE_LIST) {
            mColumnCount = 1;
            for (int i = startRow; i < lines.length; i++) {
                String rowLine = lines[i];
                if (i == lines.length - 1 && rowLine.isEmpty() && !mParsedRows.isEmpty()) {
                    continue;
                }
                mParsedRows.add(new String[]{rowLine});
                int w = (int) Math.ceil(Layout.getDesiredWidth(Utils.getColoredString(rowLine), paint)) + lastColPadEnd + 4;
                if (w > mTabSizes[0]) mTabSizes[0] = w;
            }
        } else {
            boolean collapse = false;
            for (int i = startRow; i < lines.length; i++) {
                if (lines[i].split("\t", -1).length > Math.max(4, headerCols.length > 0 ? headerCols.length : 4)) {
                    collapse = true;
                    break;
                }
            }
            for (int i = startRow; i < lines.length; i++) {
                String line = lines[i];
                if (i == lines.length - 1 && line.isEmpty() && !mParsedRows.isEmpty()) {
                    continue;
                }
                String[] cols = parseTabColumns(line, headerCols.length > 0 ? headerCols.length : 4, collapse);
                mColumnCount = Math.max(mColumnCount, Math.min(4, cols.length));
                mParsedRows.add(cols);
            }
            mColumnCount = Math.max(1, Math.min(4, mColumnCount));

            for (int c = 0; c < Math.min(mColumnCount, headerCols.length); c++) {
                mDialogTabField[c].setVisibility(View.VISIBLE);
                CharSequence styled = Utils.getColoredString(headerCols[c]);
                mDialogTabField[c].setText(styled);
                int pad = (c == mColumnCount - 1) ? lastColPadEnd : colPadEnd;
                int w = (int) Math.ceil(Layout.getDesiredWidth(styled, paint)) + pad + 4;
                if (w > mTabSizes[c]) mTabSizes[c] = w;
            }
            for (String[] row : mParsedRows) {
                for (int c = 0; c < Math.min(mColumnCount, row.length); c++) {
                    CharSequence styled = Utils.getColoredString(row[c]);
                    int pad = (c == mColumnCount - 1) ? lastColPadEnd : colPadEnd;
                    int w = (int) Math.ceil(Layout.getDesiredWidth(styled, paint)) + pad + 4;
                    if (w > mTabSizes[c]) mTabSizes[c] = w;
                }
            }
        }

        if (!mParsedRows.isEmpty()) {
            String[] firstRow = mParsedRows.get(0);
            mCurrentInputtext = firstRow.length > 0 ? Utils.getStringWithoutColors(firstRow[0]) : "";
        }
    }

    private void applyAlignedSizes() {
        for (int c = 0; c < 4; c++) {
            if (c < mColumnCount && mDialogTabField[c].getVisibility() == View.VISIBLE) {
                ViewGroup.LayoutParams lpCol = mDialogTabField[c].getLayoutParams();
                lpCol.width = mTabSizes[c];
                mDialogTabField[c].setLayoutParams(lpCol);
            }
        }

        ViewGroup.LayoutParams listLp = mDialogList.getLayoutParams();
        listLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mDialogList.setLayoutParams(listLp);

        ViewGroup.LayoutParams rvLp = mDialogListRecycler.getLayoutParams();
        rvLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mDialogListRecycler.setLayoutParams(rvLp);

        mDialogBody.post(() -> {
            if (mListAdapter == null) return;
            int bodyW = mDialogBody.getWidth();
            int listW = mDialogList.getWidth();
            if (bodyW > listW && bodyW > 0) {
                ViewGroup.LayoutParams lp1 = mDialogListLayout.getLayoutParams();
                lp1.width = bodyW;
                mDialogListLayout.setLayoutParams(lp1);

                ViewGroup.LayoutParams lp2 = mDialogList.getLayoutParams();
                lp2.width = bodyW;
                mDialogList.setLayoutParams(lp2);

                ViewGroup.LayoutParams lp3 = mDialogListRecycler.getLayoutParams();
                lp3.width = 0;
                mDialogListRecycler.setLayoutParams(lp3);

                mListAdapter.setMatchParent(true);
            } else {
                mListAdapter.setMatchParent(false);
            }
        });
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
        } else if (style == DIALOG_STYLE_INPUT) {
            mDialogTextLayout.setVisibility(View.VISIBLE);
            mDialogInputLayout.setVisibility(View.VISIBLE);
            mDialogInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            mDialogListLayout.setVisibility(View.GONE);
        } else if (style == DIALOG_STYLE_PASSWORD) {
            mDialogTextLayout.setVisibility(View.VISIBLE);
            mDialogInputLayout.setVisibility(View.VISIBLE);
            mDialogInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            mDialogListLayout.setVisibility(View.GONE);
        } else if (style == DIALOG_STYLE_LIST || style == DIALOG_STYLE_TABLIST) {
            mDialogTextLayout.setVisibility(View.GONE);
            mDialogInputLayout.setVisibility(View.GONE);
            mDialogListLayout.setVisibility(View.VISIBLE);
            mDialogTabListRow.setVisibility(View.GONE);
        } else if (style == DIALOG_STYLE_TABLIST_HEADERS) {
            mDialogTextLayout.setVisibility(View.GONE);
            mDialogInputLayout.setVisibility(View.GONE);
            mDialogListLayout.setVisibility(View.VISIBLE);
            mDialogTabListRow.setVisibility(View.VISIBLE);
        }
    }

    private void loadRecycler() {
        mListAdapter = new DialogAdapter(mParsedRows, mColumnCount, mTabSizes);
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
