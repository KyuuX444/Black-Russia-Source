package com.ryzn.launcher.other;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Shared UI micro-interaction helper providing responsive tactile press/hover
 * feedback and smooth dialog/panel entrance transitions.
 */
public final class MangaUiHelper {

    private static final float SCALE_PRESSED = 0.965f;
    private static final float SCALE_HOVERED = 1.012f;
    private static final float SCALE_NORMAL = 1.0f;
    private static final DecelerateInterpolator DECELERATE = new DecelerateInterpolator();
    private static final OvershootInterpolator OVERSHOOT = new OvershootInterpolator(1.25f);

    private MangaUiHelper() {
    }

    public static void attachPressFeedback(View... views) {
        if (views == null) return;
        for (View view : views) {
            if (view == null) continue;
            view.setOnTouchListener((v, event) -> {
                if (!v.isEnabled()) return false;
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    v.animate()
                            .scaleX(SCALE_PRESSED)
                            .scaleY(SCALE_PRESSED)
                            .setDuration(85L)
                            .setInterpolator(DECELERATE)
                            .start();
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    v.animate()
                            .scaleX(SCALE_NORMAL)
                            .scaleY(SCALE_NORMAL)
                            .setDuration(140L)
                            .setInterpolator(OVERSHOOT)
                            .start();
                }
                return false;
            });

            view.setOnHoverListener((v, event) -> {
                if (!v.isEnabled()) return false;
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_HOVER_ENTER) {
                    v.animate()
                            .scaleX(SCALE_HOVERED)
                            .scaleY(SCALE_HOVERED)
                            .setDuration(100L)
                            .setInterpolator(DECELERATE)
                            .start();
                } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
                    v.animate()
                            .scaleX(SCALE_NORMAL)
                            .scaleY(SCALE_NORMAL)
                            .setDuration(120L)
                            .setInterpolator(DECELERATE)
                            .start();
                }
                return false;
            });
        }
    }

    public static void animateEntry(View view) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(14f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(DECELERATE)
                .start();
    }
}
