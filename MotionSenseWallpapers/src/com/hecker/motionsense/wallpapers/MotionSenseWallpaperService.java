/*
 * SPDX-FileCopyrightText: 2026 Hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hecker.motionsense.wallpapers;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.model.Marker;

import java.util.ArrayDeque;

/**
 * A live wallpaper that plays a Lottie animation and reacts to the user.
 *
 * Each animation has the same segments, as Lottie markers: idle (loops), wake, wave,
 * left, right, nod, sleep, asleep (loops) and wakeup. Subclasses only pick the animation.
 *
 * Reactions: coming back to the home screen or unlocking wakes it, a tap waves, a double
 * tap nods, swiping between home screen pages looks left or right, and after a while
 * without any of these it falls asleep. To start a reaction from elsewhere, call
 * react("wake" | "wave" | "left" | "right" | "nod" | "sleep").
 */
public abstract class MotionSenseWallpaperService extends WallpaperService {

    /** The raw resource of the Lottie animation. */
    protected abstract int getAnimation();

    @Override
    public Engine onCreateEngine() {
        return new MotionSenseEngine();
    }

    private class MotionSenseEngine extends Engine implements Choreographer.FrameCallback {

        /** Fall asleep after this long without a touch, an unlock or a page swipe. */
        private static final long SLEEP_DELAY_MS = 20_000;
        /** Ignore a repeat of the same reaction within this window. */
        private static final long DEBOUNCE_MS = 400;
        /** Two taps within this window are a double tap. */
        private static final long DOUBLE_TAP_MS = 300;
        /** Idle loops are drawn at 30 fps to save battery, reactions at 60 fps. */
        private static final long IDLE_FRAME_NS = 33_000_000L;

        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final ArrayDeque<String> mQueue = new ArrayDeque<>();
        private LottieComposition mComposition;
        private LottieDrawable mDrawable;
        private float mLastOffset = -1f;
        private long mLastTapAt;
        private OsloGestureClient mOslo;

        private boolean mVisible;
        private int mWidth;
        private int mHeight;
        private String mSegment = "idle";
        private float mStart;
        private float mEnd;
        private float mFrame;
        private long mLastFrameNanos;
        private long mLastDrawNanos;
        private String mLastGesture;
        private long mLastGestureAt;

        private final Runnable mSleep = () -> play("sleep", true);

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(true);
            mOslo = new OsloGestureClient(MotionSenseWallpaperService.this,
                    new OsloGestureClient.Callback() {
                        @Override
                        public void onReach() {
                            // Match Sidekick's approach/pet behaviour: a reach wakes a sleeping
                            // character, otherwise it acknowledges the hand with the nod marker.
                            react(isAsleep() ? "wake" : "nod");
                        }

                        @Override
                        public void onFlick(int direction) {
                            // Oslo direction enum: E/NE/SE = 1/2/8, W/NW/SW = 5/4/6.
                            if (direction == 1 || direction == 2 || direction == 8) {
                                react("right");
                            } else if (direction == 4 || direction == 5 || direction == 6) {
                                react("left");
                            } else {
                                react("wave");
                            }
                        }
                    });
            mComposition = LottieCompositionFactory.fromRawResSync(
                    MotionSenseWallpaperService.this, getAnimation()).getValue();
            mDrawable = new LottieDrawable();
            if (mComposition != null) {
                mDrawable.setComposition(mComposition);
            }
            play("idle", true);
        }

        @Override
        public void onDestroy() {
            stop();
            if (mOslo != null) {
                mOslo.destroy();
                mOslo = null;
            }
            mHandler.removeCallbacksAndMessages(null);
            super.onDestroy();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            draw();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            stop();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            if (visible) {
                mLastFrameNanos = 0;
                Choreographer.getInstance().postFrameCallback(this);
                // Back on the home screen or the lock screen: it notices you.
                if (!isPreview()) {
                    react("wake");
                    if (mOslo != null) {
                        mOslo.start();
                    }
                }
                scheduleSleep();
            } else {
                stop();
            }
        }

        private void stop() {
            Choreographer.getInstance().removeFrameCallback(this);
            mHandler.removeCallbacks(mSleep);
            if (mOslo != null) {
                mOslo.stop();
            }
        }

        // ------------------------------------------------------------ segments

        private void play(String segment, boolean now) {
            if (mComposition == null) {
                return;
            }
            Marker marker = mComposition.getMarker(segment);
            if (marker == null) {
                return;
            }
            mSegment = segment;
            mStart = marker.startFrame;
            mEnd = marker.startFrame + marker.durationFrames;
            mFrame = mStart;
        }

        private boolean isAsleep() {
            return "sleep".equals(mSegment) || "asleep".equals(mSegment);
        }

        /** Plays a reaction now, waking up first if asleep. */
        private void react(String segment) {
            long now = SystemClock.uptimeMillis();
            if (segment.equals(mLastGesture) && now - mLastGestureAt < DEBOUNCE_MS) {
                return;
            }
            mLastGesture = segment;
            mLastGestureAt = now;
            mQueue.clear();
            if (isAsleep()) {
                play("wakeup", true);
                mQueue.add(segment);
            } else {
                play(segment, true);
            }
            scheduleSleep();
        }

        private void onSegmentEnd() {
            if (!mQueue.isEmpty()) {
                play(mQueue.poll(), true);
            } else if (isAsleep()) {
                play("asleep", true);
            } else {
                play("idle", true);
            }
        }

        private void scheduleSleep() {
            mHandler.removeCallbacks(mSleep);
            if (mVisible && !isPreview()) {
                mHandler.postDelayed(mSleep, SLEEP_DELAY_MS);
            }
        }

        // ------------------------------------------------------------ input

        @Override
        public void onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP
                    && event.getEventTime() - event.getDownTime() < 300) {
                long now = event.getEventTime();
                if (now - mLastTapAt < DOUBLE_TAP_MS) {
                    react("nod");
                    mLastTapAt = 0;
                } else {
                    react("wave");
                    mLastTapAt = now;
                }
            }
            super.onTouchEvent(event);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep,
                float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            // Swiping to another home screen page: look the way the pages move.
            if (xOffsetStep > 0 && mLastOffset >= 0) {
                float delta = xOffset - mLastOffset;
                if (Math.abs(delta) >= xOffsetStep * 0.5f) {
                    react(delta > 0 ? "right" : "left");
                    mLastOffset = xOffset;
                }
            } else {
                mLastOffset = xOffset;
            }
        }

        // ------------------------------------------------------------ drawing

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!mVisible) {
                return;
            }
            if (mLastFrameNanos != 0 && mComposition != null) {
                float dt = (frameTimeNanos - mLastFrameNanos) / 1_000_000_000f;
                mFrame += dt * mComposition.getFrameRate();
                if (mFrame >= mEnd) {
                    onSegmentEnd();
                }
            }
            mLastFrameNanos = frameTimeNanos;
            boolean idle = "idle".equals(mSegment) || "asleep".equals(mSegment);
            if (!idle || frameTimeNanos - mLastDrawNanos >= IDLE_FRAME_NS) {
                mLastDrawNanos = frameTimeNanos;
                draw();
            }
            Choreographer.getInstance().postFrameCallback(this);
        }

        private void draw() {
            if (mComposition == null || mWidth == 0 || mHeight == 0) {
                return;
            }
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockHardwareCanvas();
                if (canvas == null) {
                    return;
                }
                canvas.drawColor(Color.BLACK);
                Rect bounds = mComposition.getBounds();
                // Fill the screen, cropping the edges of the animation if the aspect differs.
                float scale = Math.max(mWidth / (float) bounds.width(), mHeight / (float) bounds.height());
                canvas.save();
                canvas.translate((mWidth - bounds.width() * scale) / 2f, (mHeight - bounds.height() * scale) / 2f);
                canvas.scale(scale, scale);
                mDrawable.setBounds(0, 0, bounds.width(), bounds.height());
                mDrawable.setFrame((int) mFrame);
                mDrawable.draw(canvas);
                canvas.restore();
            } catch (IllegalStateException | IllegalArgumentException e) {
                // the surface went away between frames
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (IllegalStateException | IllegalArgumentException e) {
                        // ignore
                    }
                }
            }
        }
    }
}
