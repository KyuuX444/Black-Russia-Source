package com.ryzn.game.ui.widgets;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

public class HeightProvider extends PopupWindow implements ViewTreeObserver.OnGlobalLayoutListener {
    private final Activity mActivity;
    private final View rootView;
    private Listener listener;
    private int heightMaxHorizontal;
    private int heightMaxVertical;

    public HeightProvider(final Activity activity) {
        super(activity);
        this.mActivity = activity;

        rootView = new View(activity);
        setContentView(rootView);

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        setBackgroundDrawable(new ColorDrawable(0));

        setWidth(0);
        setHeight(LinearLayout.LayoutParams.MATCH_PARENT);
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
    }

    public HeightProvider init(View view) {
        if (!isShowing() && view != null) {
            final View finalView = view;
            view.post(() -> {
                if (!mActivity.isFinishing()) {
                    showAtLocation(finalView, Gravity.NO_GRAVITY, 0, 0);
                }
            });
        }
        return this;
    }

    public HeightProvider setHeightListener(Listener listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public void onGlobalLayout() {
        Rect rect = new Rect();
        rootView.getWindowVisibleDisplayFrame(rect);
        int keyboardHeight;
        if (rect.bottom > rect.right) {
            if (rect.bottom > heightMaxVertical) {
                heightMaxVertical = rect.bottom;
            }
            keyboardHeight = heightMaxVertical - rect.bottom;
        } else {
            if (rect.bottom > heightMaxHorizontal) {
                heightMaxHorizontal = rect.bottom;
            }
            keyboardHeight = heightMaxHorizontal - rect.bottom;
        }

        if (listener != null) {
            listener.onHeightChanged(mActivity.getResources().getConfiguration().orientation, keyboardHeight);
        }
    }

    public interface Listener {
        void onHeightChanged(int orientation, int height);
    }
}
