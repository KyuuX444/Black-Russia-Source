package com.ryzn.game.gui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Interactive PC SA-MP scrollbar using sprites sliced from sampgui.png:
 * top up-arrow, middle track, movable proportional thumb, and bottom down-arrow.
 */
public class PcScrollbarView extends View {

    private SampGuiSkin skin;
    private RecyclerView targetRecycler;
    private ScrollView targetScrollView;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect srcRect = new Rect();
    private final Rect dstRect = new Rect();

    private float thumbTop = 0f;
    private float thumbHeight = 0f;
    private boolean canScroll = false;
    private boolean dragging = false;
    private float dragOffsetY = 0f;

    private final Runnable repeatScrollUp = new Runnable() {
        @Override
        public void run() {
            if (!isPressed() || dragging) return;
            scrollByStep(-1);
            postDelayed(this, 85);
        }
    };

    private final Runnable repeatScrollDown = new Runnable() {
        @Override
        public void run() {
            if (!isPressed() || dragging) return;
            scrollByStep(1);
            postDelayed(this, 85);
        }
    };

    public PcScrollbarView(Context context) {
        super(context);
    }

    public PcScrollbarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public PcScrollbarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSkin(SampGuiSkin skin) {
        this.skin = skin;
        invalidate();
    }

    public void attachToRecyclerView(RecyclerView rv) {
        this.targetRecycler = rv;
        this.targetScrollView = null;
        if (rv != null) {
            rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    syncFromTarget();
                }
            });
            rv.getViewTreeObserver().addOnGlobalLayoutListener(this::syncFromTarget);
        }
        syncFromTarget();
    }

    public void attachToScrollView(ScrollView sv) {
        this.targetScrollView = sv;
        this.targetRecycler = null;
        if (sv != null) {
            sv.getViewTreeObserver().addOnScrollChangedListener(this::syncFromTarget);
            sv.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    syncFromTarget();
                }
            });
        }
        syncFromTarget();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            post(this::syncFromTarget);
        }
    }

    public void syncFromTarget() {
        int extent = 0;
        int range = 0;
        int offset = 0;

        if (targetRecycler != null) {
            extent = targetRecycler.computeVerticalScrollExtent();
            range = targetRecycler.computeVerticalScrollRange();
            offset = targetRecycler.computeVerticalScrollOffset();
        } else if (targetScrollView != null && targetScrollView.getChildCount() > 0) {
            extent = targetScrollView.getHeight();
            range = targetScrollView.getChildAt(0).getHeight();
            offset = targetScrollView.getScrollY();
        }

        boolean scrollable = range > extent + 2 && extent > 0;
        if (canScroll != scrollable) {
            canScroll = scrollable;
            setVisibility(canScroll ? VISIBLE : GONE);
        }
        if (!canScroll) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float btnH = getArrowBtnHeight(w, h);
        float trackH = Math.max(1f, h - 2f * btnH);
        float ratio = extent / (float) range;
        float minThumb = Math.min(trackH * 0.6f, Math.max(w * 1.1f, trackH * 0.14f));
        thumbHeight = Math.max(minThumb, Math.min(trackH * 0.9f, trackH * ratio));

        float maxScroll = Math.max(1f, range - extent);
        float progress = Math.max(0f, Math.min(1f, offset / maxScroll));
        thumbTop = btnH + progress * Math.max(0f, trackH - thumbHeight);
        invalidate();
    }

    private float getArrowBtnHeight(int w, int h) {
        float ideal = w * 0.85f;
        return Math.min(h * 0.25f, Math.max(10f, ideal));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!canScroll || skin == null) return;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float btnH = getArrowBtnHeight(w, h);
        int btnBottomTop = Math.round(h - btnH);

        // Track middle behind arrows/thumb
        if (skin.scrollTrackBmp != null) {
            srcRect.set(0, 0, skin.scrollTrackBmp.getWidth(), skin.scrollTrackBmp.getHeight());
            dstRect.set(0, Math.round(btnH), w, btnBottomTop);
            canvas.drawBitmap(skin.scrollTrackBmp, srcRect, dstRect, paint);
        }
        // Top arrow
        if (skin.scrollUpBmp != null) {
            srcRect.set(0, 0, skin.scrollUpBmp.getWidth(), skin.scrollUpBmp.getHeight());
            dstRect.set(0, 0, w, Math.round(btnH));
            canvas.drawBitmap(skin.scrollUpBmp, srcRect, dstRect, paint);
        }
        // Bottom arrow
        if (skin.scrollDownBmp != null) {
            srcRect.set(0, 0, skin.scrollDownBmp.getWidth(), skin.scrollDownBmp.getHeight());
            dstRect.set(0, btnBottomTop, w, h);
            canvas.drawBitmap(skin.scrollDownBmp, srcRect, dstRect, paint);
        }
        // Thumb (slightly inset horizontally like PC SA-MP)
        if (skin.scrollThumbBmp != null && thumbHeight > 2f) {
            int padX = Math.max(1, Math.round(w * 0.14f));
            int tw = skin.scrollThumbBmp.getWidth();
            int th = skin.scrollThumbBmp.getHeight();
            int cap = Math.min(th / 3, Math.max(2, Math.round(th * 0.22f)));
            int dstCap = Math.min(Math.round(thumbHeight / 2.5f), Math.round(cap * ((w - 2f * padX) / Math.max(1f, tw))));

            int tTop = Math.round(thumbTop);
            int tBot = Math.round(thumbTop + thumbHeight);

            // Top cap
            srcRect.set(0, 0, tw, cap);
            dstRect.set(padX, tTop, w - padX, tTop + dstCap);
            canvas.drawBitmap(skin.scrollThumbBmp, srcRect, dstRect, paint);
            // Middle stretch
            srcRect.set(0, cap, tw, th - cap);
            dstRect.set(padX, tTop + dstCap, w - padX, tBot - dstCap);
            canvas.drawBitmap(skin.scrollThumbBmp, srcRect, dstRect, paint);
            // Bottom cap
            srcRect.set(0, th - cap, tw, th);
            dstRect.set(padX, tBot - dstCap, w - padX, tBot);
            canvas.drawBitmap(skin.scrollThumbBmp, srcRect, dstRect, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!canScroll) return false;
        int w = getWidth();
        int h = getHeight();
        float btnH = getArrowBtnHeight(w, h);
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                removeCallbacks(repeatScrollUp);
                removeCallbacks(repeatScrollDown);
                if (y <= btnH) {
                    dragging = false;
                    scrollByStep(-1);
                    postDelayed(repeatScrollUp, 250);
                } else if (y >= h - btnH) {
                    dragging = false;
                    scrollByStep(1);
                    postDelayed(repeatScrollDown, 250);
                } else {
                    dragging = true;
                    if (y >= thumbTop && y <= thumbTop + thumbHeight) {
                        dragOffsetY = y - thumbTop;
                    } else {
                        dragOffsetY = thumbHeight * 0.5f;
                        scrollToThumbY(y - dragOffsetY, btnH, h);
                    }
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    scrollToThumbY(y - dragOffsetY, btnH, h);
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                dragging = false;
                removeCallbacks(repeatScrollUp);
                removeCallbacks(repeatScrollDown);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void scrollByStep(int dir) {
        int step = Math.max(40, Math.round(getHeight() * 0.14f)) * dir;
        if (targetRecycler != null) {
            targetRecycler.scrollBy(0, step);
        } else if (targetScrollView != null) {
            targetScrollView.scrollBy(0, step);
        }
    }

    private void scrollToThumbY(float desiredTop, float btnH, int h) {
        float trackTravel = Math.max(1f, (h - 2f * btnH) - thumbHeight);
        float progress = Math.max(0f, Math.min(1f, (desiredTop - btnH) / trackTravel));

        if (targetRecycler != null) {
            int range = targetRecycler.computeVerticalScrollRange();
            int extent = targetRecycler.computeVerticalScrollExtent();
            int curOffset = targetRecycler.computeVerticalScrollOffset();
            int targetOffset = Math.round(progress * Math.max(0, range - extent));
            targetRecycler.scrollBy(0, targetOffset - curOffset);
        } else if (targetScrollView != null && targetScrollView.getChildCount() > 0) {
            int range = targetScrollView.getChildAt(0).getHeight();
            int extent = targetScrollView.getHeight();
            int targetY = Math.round(progress * Math.max(0, range - extent));
            targetScrollView.scrollTo(0, targetY);
        }
    }
}
