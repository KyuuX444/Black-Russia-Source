package com.ryzn.game.gui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ryzn.game.gui.util.Utils;
import com.ryzn.launcher.other.RyznGameDownloader;

import java.io.File;

/**
 * Reads PC SA-MP sampgui.png directly from downloaded game data
 * (/storage/emulated/0/ryznSAMP/SAMP/sampgui.png) and slices standard 512x512 DXUT sprites.
 */
public class SampGuiSkin {

    private Bitmap atlas;
    private long loadedMtime = -1L;
    private long loadedSize = -1L;

    public Bitmap buttonNormalBmp;
    public Bitmap buttonHoverBmp;
    public Bitmap editBoxBmp;
    public Bitmap listBoxBmp;
    public Bitmap selectionBarBmp;
    public Bitmap scrollUpBmp;
    public Bitmap scrollTrackBmp;
    public Bitmap scrollDownBmp;
    public Bitmap scrollThumbBmp;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    public synchronized boolean reloadIfNeeded(Activity activity) {
        File extFile = RyznGameDownloader.getSampGuiFile();
        if (extFile.exists() && extFile.isFile() && extFile.length() > 0 && extFile.canRead()) {
            long mtime = extFile.lastModified();
            long size = extFile.length();
            if (atlas != null && mtime == loadedMtime && size == loadedSize) {
                return false;
            }
            Bitmap bmp = BitmapFactory.decodeFile(extFile.getAbsolutePath());
            if (bmp != null) {
                applyAtlas(bmp);
                loadedMtime = mtime;
                loadedSize = size;
                return true;
            }
        }

        if (atlas == null) {
            return false;
        }
        clearAtlas();
        return true;
    }

    private void clearAtlas() {
        atlas = null;
        loadedMtime = -1L;
        loadedSize = -1L;
        buttonNormalBmp = null;
        buttonHoverBmp = null;
        editBoxBmp = null;
        listBoxBmp = null;
        selectionBarBmp = null;
        scrollUpBmp = null;
        scrollTrackBmp = null;
        scrollDownBmp = null;
        scrollThumbBmp = null;
    }

    private void applyAtlas(Bitmap bmp) {
        atlas = bmp;
        float sx = bmp.getWidth() / 512.0f;
        float sy = bmp.getHeight() / 512.0f;

        buttonNormalBmp = crop(bmp, sx, sy, 2, 3, 132, 50);
        buttonHoverBmp = crop(bmp, sx, sy, 138, 2, 270, 51);
        editBoxBmp = crop(bmp, sx, sy, 7, 83, 247, 120);
        listBoxBmp = crop(bmp, sx, sy, 13, 123, 241, 265);
        selectionBarBmp = crop(bmp, sx, sy, 13, 266, 241, 289);
        scrollUpBmp = crop(bmp, sx, sy, 243, 123, 265, 141);
        scrollTrackBmp = crop(bmp, sx, sy, 243, 142, 265, 157);
        scrollDownBmp = crop(bmp, sx, sy, 243, 158, 265, 176);
        scrollThumbBmp = crop(bmp, sx, sy, 269, 126, 284, 165);
    }

    private static Bitmap crop(Bitmap src, float sx, float sy, int x0, int y0, int x1, int y1) {
        int l = Math.max(0, Math.min(src.getWidth() - 1, Math.round(x0 * sx)));
        int t = Math.max(0, Math.min(src.getHeight() - 1, Math.round(y0 * sy)));
        int r = Math.max(l + 1, Math.min(src.getWidth(), Math.round(x1 * sx)));
        int b = Math.max(t + 1, Math.min(src.getHeight(), Math.round(y1 * sy)));
        return Bitmap.createBitmap(src, l, t, r - l, b - t);
    }

    public Drawable createButtonDrawable(Activity activity) {
        if (buttonNormalBmp == null) return null;
        float edgeScale = Math.max(0.5f, Utils.scale(activity, 1.0f));
        Drawable normal = new NineSliceDrawable(buttonNormalBmp, 12, 12, 12, 12, edgeScale, paint);
        if (buttonHoverBmp == null) {
            return normal;
        }
        Drawable pressed = new NineSliceDrawable(buttonHoverBmp, 12, 12, 12, 12, edgeScale, paint);
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    public ColorStateList createButtonTextColors() {
        int normalColor = isBrightCenter(buttonNormalBmp) ? Color.BLACK : Color.WHITE;
        int pressedColor = (buttonHoverBmp != null && isBrightCenter(buttonHoverBmp)) ? Color.BLACK : Color.WHITE;
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_pressed},
                new int[]{android.R.attr.state_focused},
                new int[]{}
        };
        return new ColorStateList(states, new int[]{pressedColor, pressedColor, normalColor});
    }

    public Drawable createEditBoxDrawable(Activity activity) {
        if (editBoxBmp == null) return null;
        float edgeScale = Math.max(0.5f, Utils.scale(activity, 1.0f));
        return new NineSliceDrawable(editBoxBmp, 10, 8, 10, 8, edgeScale, paint);
    }

    public Drawable createListBoxDrawable(Activity activity) {
        if (listBoxBmp == null) return null;
        float edgeScale = Math.max(0.5f, Utils.scale(activity, 1.0f));
        return new NineSliceDrawable(listBoxBmp, 6, 6, 6, 6, edgeScale, paint);
    }

    public Drawable createSelectionBarDrawable(Activity activity) {
        if (selectionBarBmp == null) return null;
        float edgeScale = Math.max(0.5f, Utils.scale(activity, 1.0f));
        return new NineSliceDrawable(selectionBarBmp, 4, 3, 4, 3, edgeScale, paint);
    }

    private static boolean isBrightCenter(Bitmap bmp) {
        if (bmp == null || bmp.getWidth() < 4 || bmp.getHeight() < 4) return false;
        int c = bmp.getPixel(bmp.getWidth() / 2, bmp.getHeight() / 2);
        int a = Color.alpha(c);
        if (a < 80) return false;
        double lum = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c);
        return lum > 165.0;
    }

    public static class NineSliceDrawable extends Drawable {
        private final Bitmap bmp;
        private final int capL;
        private final int capT;
        private final int capR;
        private final int capB;
        private final float edgeScale;
        private final Paint paint;
        private final Rect src = new Rect();
        private final Rect dst = new Rect();

        public NineSliceDrawable(Bitmap bmp, int capL, int capT, int capR, int capB, float edgeScale, Paint paint) {
            this.bmp = bmp;
            float normW = bmp.getWidth() / 130.0f;
            float normH = bmp.getHeight() / 47.0f;
            this.capL = Math.max(1, Math.min(bmp.getWidth() / 3, Math.round(capL * Math.min(1.5f, Math.max(0.5f, normW)))));
            this.capT = Math.max(1, Math.min(bmp.getHeight() / 3, Math.round(capT * Math.min(1.5f, Math.max(0.5f, normH)))));
            this.capR = Math.max(1, Math.min(bmp.getWidth() / 3, Math.round(capR * Math.min(1.5f, Math.max(0.5f, normW)))));
            this.capB = Math.max(1, Math.min(bmp.getHeight() / 3, Math.round(capB * Math.min(1.5f, Math.max(0.5f, normH)))));
            this.edgeScale = edgeScale;
            this.paint = paint;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            int w = b.width();
            int h = b.height();
            if (w <= 0 || h <= 0 || bmp == null || bmp.isRecycled()) return;

            int sw = bmp.getWidth();
            int sh = bmp.getHeight();
            int dl = Math.min(w / 2, Math.max(2, Math.round(capL * edgeScale)));
            int dr = Math.min(w - dl, Math.max(2, Math.round(capR * edgeScale)));
            int dt = Math.min(h / 2, Math.max(2, Math.round(capT * edgeScale)));
            int db = Math.min(h - dt, Math.max(2, Math.round(capB * edgeScale)));

            int[] sx = {0, capL, sw - capR, sw};
            int[] sy = {0, capT, sh - capB, sh};
            int[] dx = {b.left, b.left + dl, b.right - dr, b.right};
            int[] dy = {b.top, b.top + dt, b.bottom - db, b.bottom};

            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    if (sx[c] >= sx[c + 1] || sy[r] >= sy[r + 1]) continue;
                    if (dx[c] >= dx[c + 1] || dy[r] >= dy[r + 1]) continue;
                    src.set(sx[c], sy[r], sx[c + 1], sy[r + 1]);
                    dst.set(dx[c], dy[r], dx[c + 1], dy[r + 1]);
                    canvas.drawBitmap(bmp, src, dst, paint);
                }
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
