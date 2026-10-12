/*
 * SPDX-FileCopyrightText: 2026 Hecker
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hecker.motionsense.wallpapers;

import android.graphics.Canvas;
import android.graphics.Rect;
import com.airbnb.lottie.LottieDrawable;

/** Shares the wallpaper's fit, parallax and tilt drawing with native frame tests. */
public final class SidekickRenderer {
    /** Keep fractional source frames when the display and composition rates differ. */
    public void setFrame(LottieDrawable drawable, float frame) {
        if (drawable.getComposition() == null) {
            return;
        }
        float start = drawable.getComposition().getStartFrame();
        float span = drawable.getComposition().getEndFrame() - start;
        if (span > 0f) {
            drawable.setProgress(Math.max(0f, Math.min(1f, (frame - start) / span)));
        }
    }

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
                height / (float) bounds.height()) * overscan * sceneScale;
        int checkpoint = canvas.save();
        try {
            canvas.rotate(rotation, width * .5f, height * .53f);
            canvas.translate((width - bounds.width() * scale) * .5f + parallaxX,
                    (height - bounds.height() * scale) * .5f + parallaxY);
            canvas.scale(scale, scale);
            drawable.setBounds(0, 0, bounds.width(), bounds.height());
            drawable.draw(canvas);
        } finally {
            canvas.restoreToCount(checkpoint);
        }
    }
}
