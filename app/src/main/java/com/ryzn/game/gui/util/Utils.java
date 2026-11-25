package com.ryzn.game.gui.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Pattern;

public class Utils {
    static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static SecureRandom rnd = new SecureRandom();
    private static final float MULT_X = 5.2083336E-4f;
    private static final float MULT_Y = 9.259259E-4f;

    public static void ShowLayout(View view, boolean isAnim) {
        if (view != null) {
            view.setVisibility(View.VISIBLE);
            if (isAnim) {
                fadeIn(view);
            } else {
                view.setAlpha(1.0f);
            }
        }
    }

    public static void HideLayout(View view, boolean isAnim) {
        if (view != null) {
            if (isAnim) {
                fadeOut(view);
                return;
            }
            view.setAlpha(0.0f);
            view.setVisibility(View.GONE);
        }
    }

    private static void fadeIn(View view) {
        if (view != null) {
            view.animate().setDuration(250).setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                }
            }).alpha(1.0f);
        }
    }

    private static void fadeOut(final View view) {
        if (view != null) {
            view.animate().setDuration(250).setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(View.GONE);
                    super.onAnimationEnd(animation);
                }
            }).alpha(0.0f);
        }
    }

    public static Drawable getRes(Activity activity, int id) {
        return ContextCompat.getDrawable(activity.getApplicationContext(), id);
    }

    public static Spanned transfromColors(String str) {
        if (str == null) str = "";
        int i;
        LinkedList<String> linkedList = new LinkedList<>();
        int i2 = 0;
        String str2 = str;
        int i3 = 0;
        for (int i4 = 0; i4 < str2.length(); i4++) {
            if (str2.charAt(i4) == '{' && (i = i4 + 7) < str2.length() && str2.charAt(i) == '}') {
                int i5 = i4 + 1;
                linkedList.addLast("#" + str2.substring(i5, i));
                str2 = str2.substring(0, i5) + "repl" + i3 + str2.substring(i);
                i3++;
            }
        }
        Iterator<String> it = linkedList.iterator();
        while (it.hasNext()) {
            String str3 = it.next();
            if (i2 == 0) {
                str2 = str2.replaceAll(Pattern.quote("{repl" + i2 + "}"), "<font color='" + str3 + "'>");
            } else {
                str2 = str2.replaceAll(Pattern.quote("{repl" + i2 + "}"), "</font><font color='" + str3 + "'>");
            }
            i2++;
        }
        if (linkedList.size() >= 1) {
            str2 = str2 + "</font>";
        }
        return Html.fromHtml(str2.replaceAll(Pattern.quote("\n"), "<br>"));
    }

    public static CharSequence getColoredString(String str) {
        return transfromColors(str);
    }

    public static String getStringWithoutColors(String str) {
        if (str == null) return "";
        int i;
        StringBuilder sb = new StringBuilder();
        int i2 = 0;
        while (i2 < str.length()) {
            if (str.charAt(i2) == '{' && (i = i2 + 7) < str.length() && str.charAt(i) == '}') {
                i2 = i;
            } else {
                sb.append(str.charAt(i2));
            }
            i2++;
        }
        return sb.toString();
    }

    public static void scaleViewAndChildren(Activity activity, View view) {
        if (activity == null || view == null) return;
        Display defaultDisplay = activity.getWindowManager().getDefaultDisplay();
        Point point = new Point();
        defaultDisplay.getSize(point);
        float min = Math.min(((float) point.x) * MULT_X, ((float) point.y) * MULT_Y);
        if (min <= 0f) min = 1.0f;

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null) {
            if (!(layoutParams.width == -1 || layoutParams.width == -2 || ((int) (((float) layoutParams.width) * min)) == 0)) {
                layoutParams.width = (int) (((float) layoutParams.width) * min);
            }
            if (!(layoutParams.height == -1 || layoutParams.height == -2 || ((int) (((float) layoutParams.height) * min)) == 0)) {
                layoutParams.height = (int) (((float) layoutParams.height) * min);
            }
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
                marginLayoutParams.leftMargin = (int) (((float) marginLayoutParams.leftMargin) * min);
                marginLayoutParams.rightMargin = (int) (((float) marginLayoutParams.rightMargin) * min);
                marginLayoutParams.topMargin = (int) (((float) marginLayoutParams.topMargin) * min);
                marginLayoutParams.bottomMargin = (int) (((float) marginLayoutParams.bottomMargin) * min);
            }
            if (layoutParams instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams layoutParams2 = (ConstraintLayout.LayoutParams) layoutParams;
                layoutParams2.matchConstraintMinHeight = (int) (((float) layoutParams2.matchConstraintMinHeight) * min);
                layoutParams2.matchConstraintMinWidth = (int) (((float) layoutParams2.matchConstraintMinWidth) * min);
                layoutParams2.matchConstraintMaxHeight = (int) (((float) layoutParams2.matchConstraintMaxHeight) * min);
                layoutParams2.matchConstraintMaxWidth = (int) (((float) layoutParams2.matchConstraintMaxWidth) * min);
            }
            view.setLayoutParams(layoutParams);
        }
        view.setPadding(
                (int) (((float) view.getPaddingLeft()) * min),
                (int) (((float) view.getPaddingTop()) * min),
                (int) (((float) view.getPaddingRight()) * min),
                (int) (((float) view.getPaddingBottom()) * min));
        view.setMinimumHeight((int) (((float) view.getMinimumHeight()) * min));
        view.setMinimumWidth((int) (((float) view.getMinimumWidth()) * min));
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextSize(0, textView.getTextSize() * min);
        }
        if (view instanceof RecyclerView) {
            RecyclerView rv = (RecyclerView) view;
            rv.setScrollBarSize((int) (((float) rv.getScrollBarSize()) * min));
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                scaleViewAndChildren(activity, viewGroup.getChildAt(i));
            }
        }
    }

    public static float scale(Activity activity, float f) {
        Display defaultDisplay = activity.getWindowManager().getDefaultDisplay();
        Point point = new Point();
        defaultDisplay.getSize(point);
        return f * Math.min(((float) point.x) * MULT_X, ((float) point.y) * MULT_Y);
    }

    public static int getTextWidth(String str, TextPaint textPaint) {
        return (int) new StaticLayout(str != null ? str : "", textPaint, 10000, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false).getLineWidth(0);
    }

    public static ArrayList<String> fixFieldsForDialog(ArrayList<String> fields) {
        ArrayList<String> newList = new ArrayList<>();
        int max = 0;
        for (int i = 0; i < fields.size(); i++) {
            int len = fields.get(i).split("\t").length;
            if (len > max) max = len;
        }
        for (int i2 = 0; i2 < fields.size(); i2++) {
            StringBuilder field = new StringBuilder(fields.get(i2));
            for (int len2 = fields.get(i2).split("\t").length; len2 != max; len2++) {
                field.append("\t ");
            }
            newList.add(field.toString());
        }
        return newList;
    }

    public static String randomString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(AB.charAt(rnd.nextInt(AB.length())));
        }
        return sb.toString();
    }

    public static int getTextLength(TextView textView) {
        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(textView.getTextSize());
        textPaint.setTypeface(textView.getTypeface());
        return (int) textPaint.measureText(textView.getText().toString());
    }

    public static void makeAllViewsVisible(ViewGroup viewGroup) {
        viewGroup.setVisibility(View.VISIBLE);
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            if (viewGroup.getChildAt(i) instanceof ViewGroup) {
                makeAllViewsVisible((ViewGroup) viewGroup.getChildAt(i));
            } else {
                viewGroup.getChildAt(i).setVisibility(View.VISIBLE);
            }
        }
    }

    public static void changeTextViewWidth(TextView textView) {
        textView.post(() -> {
            TextPaint textPaint = new TextPaint();
            textPaint.setTextSize(textView.getTextSize());
            textPaint.setTypeface(textView.getTypeface());
            StaticLayout staticLayout = new StaticLayout(textView.getText(), textPaint, 10000, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            int lineCount = staticLayout.getLineCount();
            float lineWidth = staticLayout.getLineWidth(0);
            for (int i = 0; i < lineCount; i++) {
                if (staticLayout.getLineWidth(i) >= lineWidth) {
                    lineWidth = staticLayout.getLineWidth(i) + 5.0f;
                }
            }
            ViewGroup.LayoutParams layoutParams = textView.getLayoutParams();
            layoutParams.width = (int) lineWidth;
            textView.setLayoutParams(layoutParams);
        });
    }
}
