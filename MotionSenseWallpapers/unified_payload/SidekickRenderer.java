/*
 * SPDX-FileCopyrightText: 2026 Hecker
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hecker.motionsense.wallpapers;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.RenderMode;

/** Rasterizes vectors with an identity canvas before submitting a frame to the surface. */
public final class SidekickRenderer {
    private static final int MAX_EDGE = 4096;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private Bitmap bitmap;
    private Canvas bitmapCanvas;

    public void draw(Canvas canvas, LottieDrawable drawable, int width, int height,
            float overscan, float sceneScale, float rotation, float parallaxX, float parallaxY) {
        if (width <= 0 || height <= 0 || drawable.getComposition() == null) {
            return;
        }
        Rect bounds = drawable.getComposition().getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        float scale = Math.max(width / (float) bounds.width(),
                height / (float) bounds.height()) * overscan;
        float displayWidth = bounds.width() * scale;
        float displayHeight = bounds.height() * scale;
        float bufferScale = Math.min(1f, MAX_EDGE / Math.max(displayWidth, displayHeight));
        int bufferWidth = Math.max(1, Math.round(displayWidth * bufferScale));
        int bufferHeight = Math.max(1, Math.round(displayHeight * bufferScale));
        if (bitmap == null || bitmap.getWidth() != bufferWidth || bitmap.getHeight() != bufferHeight) {
            clear();
            bitmap = Bitmap.createBitmap(bufferWidth, bufferHeight, Bitmap.Config.ARGB_8888);
            bitmap.setDensity(Bitmap.DENSITY_NONE);
            bitmapCanvas = new Canvas(bitmap);
        }
        bitmap.eraseColor(0);
        // HARDWARE selects Lottie's direct draw path. The target is our software bitmap
        // canvas: it has no surface transforms or GPU path tessellation to inherit.
        drawable.setRenderMode(RenderMode.HARDWARE);
        drawable.setBounds(0, 0, bufferWidth, bufferHeight);
        drawable.draw(bitmapCanvas);

        displayWidth *= sceneScale;
        displayHeight *= sceneScale;
        float left = (width - displayWidth) * .5f + parallaxX;
        float top = (height - displayHeight) * .5f + parallaxY;
        destination.set(left, top, left + displayWidth, top + displayHeight);
        int checkpoint = canvas.save();
        try {
            canvas.rotate(rotation, width * .5f, height * .53f);
            canvas.drawBitmap(bitmap, null, destination, paint);
        } finally {
            canvas.restoreToCount(checkpoint);
        }
    }

    public void clear() {
        bitmapCanvas = null;
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }
}
