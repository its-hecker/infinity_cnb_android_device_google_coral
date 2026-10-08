/*
 * SPDX-FileCopyrightText: 2026 Hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hecker.motionsense.wallpapers;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Motion Sense aware Sidekick renderer.
 *
 * Core packs use the standard markers idle, wake, wave, left, right, nod, sleep, asleep and
 * wakeup. Extra packs may additionally expose tap, pet, special, up, look_left and look_right.
 * Missing optional markers gracefully fall back to the standard marker set.
 */
public abstract class MotionSenseWallpaperService extends WallpaperService {

    private static final String PREFS_NAME = "sidekick_wallpaper";

    /** Built-in Sidekick characters, in double-tap cycle order. */
    private static final int[] CORE_CHARACTER_ANIMATIONS = {
            R.raw.wallpaper_hecker,
            R.raw.wallpaper_mochi,
            R.raw.wallpaper_biscuit,
            R.raw.wallpaper_whisk_crumb,
            R.raw.wallpaper_pip,
            R.raw.wallpaper_drift,
            R.raw.wallpaper_orbit,
            R.raw.wallpaper_aurora,
    };

    /**
     * Optional drop-in packs. They are intentionally looked up by resource name so the engine
     * already supports these slots without bundling third-party artwork in the device tree.
     */
    private static final String[] OPTIONAL_CHARACTER_RESOURCES = {
            "wallpaper_doraemon",
            "wallpaper_ben10",
            "wallpaper_batman",
            "wallpaper_tom_jerry",
    };

    /** The default raw resource for this wallpaper entry. */
    protected abstract int getAnimation();

    @Override
    public Engine onCreateEngine() {
        return new MotionSenseEngine();
    }

    private class MotionSenseEngine extends Engine implements Choreographer.FrameCallback {

        private static final long SLEEP_DELAY_MS = 20_000;
        private static final long DEBOUNCE_MS = 400;
        private static final long DOUBLE_TAP_MS = 300;
        private static final long LONG_PRESS_MS = 575;
        private static final long AMBIENT_REACTION_MS = 10_500;
        private static final long PARALLAX_RETURN_MS = 650;
        private static final long IDLE_FRAME_NS = 33_000_000L;
        private static final float DEPTH_SCALE = 1.035f;

        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final ArrayDeque<String> mQueue = new ArrayDeque<>();
        private final Paint mVignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private SharedPreferences mPrefs;
        private int[] mAvailableAnimations;
        private LottieComposition mComposition;
        private LottieDrawable mDrawable;
        private int mAnimationIndex;
        private int mCurrentAnimationRes;
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

        private float mLastOffset = -1f;
        private float mLastReactionOffset = -1f;
        private float mParallaxX;
        private float mParallaxTargetX;

        private boolean mTouchActive;
        private boolean mLongPressTriggered;
        private float mDownX;
        private float mDownY;
        private float mTouchThresholdPx;
        private long mLastTapAt;
        private int mAmbientStep;

        private final Runnable mSingleTap = () -> {
            if (mLastTapAt != 0) {
                mLastTapAt = 0;
                reactFirstAvailable("tap", "wave", "nod");
            }
        };

        private final Runnable mLongPress = () -> {
            if (!mTouchActive || !mVisible) {
                return;
            }
            mLongPressTriggered = true;
            mLastTapAt = 0;
            mHandler.removeCallbacks(mSingleTap);
            reactFirstAvailable("special", "pet", "nod", "wave");
        };

        private final Runnable mSleep = () -> {
            mHandler.removeCallbacks(mAmbientReaction);
            playFirstAvailable("sleep", "asleep");
        };

        private final Runnable mAmbientReaction = new Runnable() {
            @Override
            public void run() {
                if (!mVisible || isPreview() || isAsleep()) {
                    return;
                }
                if ("idle".equals(mSegment)) {
                    switch (mAmbientStep++ % 3) {
                        case 0:
                            playAmbient("look_left", "left", "nod");
                            break;
                        case 1:
                            playAmbient("look_right", "right", "nod");
                            break;
                        default:
                            playAmbient("nod", "wave");
                            break;
                    }
                }
                scheduleAmbient();
            }
        };

        private final Runnable mResetParallax = () -> mParallaxTargetX = 0f;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(true);

            mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            mAvailableAnimations = buildAvailableAnimations();
            int initial = resolveSavedAnimation(getAnimation());
            mAnimationIndex = findAnimationIndex(initial);
            mTouchThresholdPx = 48f * getResources().getDisplayMetrics().density;

            mOslo = new OsloGestureClient(MotionSenseWallpaperService.this,
                    new OsloGestureClient.Callback() {
                        @Override
                        public void onPresence() {
                            if (isAsleep()) {
                                reactFirstAvailable("wake", "wakeup");
                            }
                        }

                        @Override
                        public void onReach() {
                            pulseParallax(0f);
                            if (isAsleep()) {
                                reactFirstAvailable("wake", "wakeup");
                            } else {
                                reactFirstAvailable("pet", "nod", "wave");
                            }
                        }

                        @Override
                        public void onFlick(int direction) {
                            reactForDirection(direction);
                        }

                        @Override
                        public void onSwipe(int direction) {
                            reactForDirection(direction);
                        }
                    });

            loadAnimation(mAvailableAnimations[mAnimationIndex], "idle");
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
            float radius = Math.max(width, height) * 0.78f;
            mVignettePaint.setShader(new RadialGradient(
                    width * 0.5f,
                    height * 0.44f,
                    radius,
                    new int[] {
                            Color.TRANSPARENT,
                            Color.argb(12, 0, 0, 0),
                            Color.argb(58, 0, 0, 0)
                    },
                    new float[] {0f, 0.68f, 1f},
                    Shader.TileMode.CLAMP));
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
                if (!isPreview()) {
                    reactFirstAvailable("wake", "wakeup", "nod");
                    if (mOslo != null) {
                        mOslo.start();
                    }
                }
                scheduleSleep();
                scheduleAmbient();
            } else {
                stop();
            }
        }

        private void stop() {
            Choreographer.getInstance().removeFrameCallback(this);
            mHandler.removeCallbacks(mSleep);
            mHandler.removeCallbacks(mAmbientReaction);
            mHandler.removeCallbacks(mSingleTap);
            mHandler.removeCallbacks(mLongPress);
            mHandler.removeCallbacks(mResetParallax);
            mTouchActive = false;
            mLastTapAt = 0;
            mParallaxTargetX = 0f;
            if (mOslo != null) {
                mOslo.stop();
            }
        }

        // ------------------------------------------------------------ characters

        private int[] buildAvailableAnimations() {
            List<Integer> ids = new ArrayList<>();
            for (int id : CORE_CHARACTER_ANIMATIONS) {
                ids.add(id);
            }
            for (String name : OPTIONAL_CHARACTER_RESOURCES) {
                int id = getResources().getIdentifier(name, "raw", getPackageName());
                if (id != 0 && !ids.contains(id)) {
                    ids.add(id);
                }
            }
            int[] result = new int[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                result[i] = ids.get(i);
            }
            return result;
        }

        private String preferenceKey() {
            return "selected." + MotionSenseWallpaperService.this.getClass().getName();
        }

        private int resolveSavedAnimation(int fallback) {
            String savedName = mPrefs.getString(preferenceKey(), null);
            if (savedName == null) {
                return fallback;
            }
            int id = getResources().getIdentifier(savedName, "raw", getPackageName());
            return findAnimationIndex(id) >= 0 ? id : fallback;
        }

        private int findAnimationIndex(int animationRes) {
            if (mAvailableAnimations == null) {
                return -1;
            }
            for (int i = 0; i < mAvailableAnimations.length; i++) {
                if (mAvailableAnimations[i] == animationRes) {
                    return i;
                }
            }
            return -1;
        }

        private boolean loadAnimation(int animationRes, String firstSegment) {
            LottieComposition composition = LottieCompositionFactory.fromRawResSync(
                    MotionSenseWallpaperService.this, animationRes).getValue();
            if (composition == null || composition.getMarker("idle") == null) {
                return false;
            }

            mComposition = composition;
            mDrawable = new LottieDrawable();
            mDrawable.setComposition(composition);
            mCurrentAnimationRes = animationRes;
            mQueue.clear();
            playFirstAvailable(firstSegment, "idle");
            mLastFrameNanos = 0;
            mLastDrawNanos = 0;
            draw();
            return true;
        }

        private void cycleCharacter() {
            if (mAvailableAnimations.length < 2) {
                return;
            }
            int oldIndex = mAnimationIndex;
            for (int step = 1; step <= mAvailableAnimations.length; step++) {
                int next = (oldIndex + step) % mAvailableAnimations.length;
                if (loadAnimation(mAvailableAnimations[next], "wake")) {
                    mAnimationIndex = next;
                    mLastGesture = null;
                    mLastGestureAt = 0;
                    String resourceName = getResources().getResourceEntryName(mCurrentAnimationRes);
                    mPrefs.edit().putString(preferenceKey(), resourceName).apply();
                    pulseParallax(0f);
                    scheduleSleep();
                    scheduleAmbient();
                    return;
                }
            }
        }

        // ------------------------------------------------------------ reactions

        private boolean hasMarker(String segment) {
            return mComposition != null && mComposition.getMarker(segment) != null;
        }

        private String firstAvailable(String... candidates) {
            for (String candidate : candidates) {
                if (hasMarker(candidate)) {
                    return candidate;
                }
            }
            return hasMarker("idle") ? "idle" : null;
        }

        private boolean play(String segment) {
            if (mComposition == null) {
                return false;
            }
            Marker marker = mComposition.getMarker(segment);
            if (marker == null) {
                return false;
            }
            mSegment = segment;
            mStart = marker.startFrame;
            mEnd = marker.startFrame + marker.durationFrames;
            mFrame = mStart;
            return true;
        }

        private void playFirstAvailable(String... candidates) {
            String segment = firstAvailable(candidates);
            if (segment != null) {
                play(segment);
            }
        }

        private void playAmbient(String... candidates) {
            String segment = firstAvailable(candidates);
            if (segment != null && !"idle".equals(segment)) {
                mQueue.clear();
                play(segment);
            }
        }

        private boolean isAsleep() {
            return "sleep".equals(mSegment) || "asleep".equals(mSegment);
        }

        private void reactFirstAvailable(String... candidates) {
            String segment = firstAvailable(candidates);
            if (segment == null) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (segment.equals(mLastGesture) && now - mLastGestureAt < DEBOUNCE_MS) {
                return;
            }
            mLastGesture = segment;
            mLastGestureAt = now;
            mQueue.clear();

            if (isAsleep() && !"sleep".equals(segment) && !"asleep".equals(segment)) {
                String wakeup = firstAvailable("wakeup", "wake");
                if (wakeup != null && !wakeup.equals(segment)) {
                    play(wakeup);
                    mQueue.add(segment);
                } else {
                    play(segment);
                }
            } else {
                play(segment);
            }
            scheduleSleep();
            scheduleAmbient();
        }

        private void onSegmentEnd() {
            if (!mQueue.isEmpty()) {
                play(mQueue.poll());
            } else if ("sleep".equals(mSegment)) {
                playFirstAvailable("asleep", "idle");
            } else if ("asleep".equals(mSegment)) {
                playFirstAvailable("asleep", "idle");
            } else {
                playFirstAvailable("idle");
            }
        }

        private void scheduleSleep() {
            mHandler.removeCallbacks(mSleep);
            if (mVisible && !isPreview()) {
                mHandler.postDelayed(mSleep, SLEEP_DELAY_MS);
            }
        }

        private void scheduleAmbient() {
            mHandler.removeCallbacks(mAmbientReaction);
            if (mVisible && !isPreview() && !isAsleep()) {
                mHandler.postDelayed(mAmbientReaction, AMBIENT_REACTION_MS);
            }
        }

        private void reactForDirection(int direction) {
            if (direction == 1 || direction == 2 || direction == 8) {
                pulseParallax(mWidth * 0.025f);
                reactFirstAvailable("right", "wave", "nod");
            } else if (direction == 4 || direction == 5 || direction == 6) {
                pulseParallax(-mWidth * 0.025f);
                reactFirstAvailable("left", "wave", "nod");
            } else {
                pulseParallax(0f);
                reactFirstAvailable("wave", "nod");
            }
        }

        // ------------------------------------------------------------ touch input

        @Override
        public void onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchActive = true;
                    mLongPressTriggered = false;
                    mDownX = event.getX();
                    mDownY = event.getY();
                    mHandler.removeCallbacks(mLongPress);
                    mHandler.postDelayed(mLongPress, LONG_PRESS_MS);
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mTouchActive) {
                        float moveX = event.getX() - mDownX;
                        float moveY = event.getY() - mDownY;
                        if (Math.hypot(moveX, moveY) > mTouchThresholdPx * 0.45f) {
                            mHandler.removeCallbacks(mLongPress);
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    mHandler.removeCallbacks(mLongPress);
                    if (!mTouchActive) {
                        break;
                    }
                    mTouchActive = false;

                    if (mLongPressTriggered) {
                        mLongPressTriggered = false;
                        break;
                    }

                    float dx = event.getX() - mDownX;
                    float dy = event.getY() - mDownY;
                    if (Math.abs(dx) >= mTouchThresholdPx || Math.abs(dy) >= mTouchThresholdPx) {
                        mLastTapAt = 0;
                        mHandler.removeCallbacks(mSingleTap);
                        if (Math.abs(dx) > Math.abs(dy)) {
                            pulseParallax(dx > 0 ? mWidth * 0.025f : -mWidth * 0.025f);
                            reactFirstAvailable(dx > 0 ? "right" : "left", "wave", "nod");
                        } else if (dy < 0) {
                            reactFirstAvailable("up", "wake", "nod", "wave");
                        } else {
                            reactFirstAvailable("sleep", "nod");
                        }
                        break;
                    }

                    long now = SystemClock.uptimeMillis();
                    if (mLastTapAt != 0 && now - mLastTapAt <= DOUBLE_TAP_MS) {
                        mHandler.removeCallbacks(mSingleTap);
                        mLastTapAt = 0;
                        cycleCharacter();
                    } else {
                        mLastTapAt = now;
                        mHandler.removeCallbacks(mSingleTap);
                        mHandler.postDelayed(mSingleTap, DOUBLE_TAP_MS);
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                    mTouchActive = false;
                    mLongPressTriggered = false;
                    mHandler.removeCallbacks(mLongPress);
                    break;

                default:
                    break;
            }
            super.onTouchEvent(event);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep,
                float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            if (mLastOffset >= 0) {
                float delta = xOffset - mLastOffset;
                if (Math.abs(delta) > 0.001f) {
                    pulseParallax(Math.max(-mWidth * 0.03f,
                            Math.min(mWidth * 0.03f, delta * mWidth * 0.35f)));
                }
            }

            // Accumulate launcher movement for the reaction threshold so smooth page scrolling
            // still produces one clear left/right response instead of being lost in tiny deltas.
            if (mLastReactionOffset < 0) {
                mLastReactionOffset = xOffset;
            } else if (xOffsetStep > 0) {
                float reactionDelta = xOffset - mLastReactionOffset;
                if (Math.abs(reactionDelta) >= xOffsetStep * 0.5f) {
                    reactFirstAvailable(reactionDelta > 0 ? "right" : "left", "wave", "nod");
                    mLastReactionOffset = xOffset;
                }
            }
            mLastOffset = xOffset;
        }

        // ------------------------------------------------------------ depth/parallax

        private void pulseParallax(float x) {
            mParallaxTargetX = x;
            mHandler.removeCallbacks(mResetParallax);
            mHandler.postDelayed(mResetParallax, PARALLAX_RETURN_MS);
        }

        private void updateParallax(float dt) {
            float amount = Math.min(1f, dt * 9f);
            mParallaxX += (mParallaxTargetX - mParallaxX) * amount;
        }

        // ------------------------------------------------------------ drawing

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!mVisible) {
                return;
            }
            if (mLastFrameNanos != 0 && mComposition != null) {
                float dt = (frameTimeNanos - mLastFrameNanos) / 1_000_000_000f;
                updateParallax(dt);
                mFrame += dt * mComposition.getFrameRate();
                if (mFrame >= mEnd) {
                    onSegmentEnd();
                }
            }
            mLastFrameNanos = frameTimeNanos;

            boolean idle = "idle".equals(mSegment) || "asleep".equals(mSegment);
            boolean movingDepth = Math.abs(mParallaxTargetX - mParallaxX) > 0.25f;
            if (!idle || movingDepth || frameTimeNanos - mLastDrawNanos >= IDLE_FRAME_NS) {
                mLastDrawNanos = frameTimeNanos;
                draw();
            }
            Choreographer.getInstance().postFrameCallback(this);
        }

        private void draw() {
            if (mComposition == null || mDrawable == null || mWidth == 0 || mHeight == 0) {
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

                // A small overscan plus parallax gives flat Lottie scenes a subtle depth effect
                // without changing the character artwork itself.
                float scale = Math.max(
                        mWidth / (float) bounds.width(),
                        mHeight / (float) bounds.height()) * DEPTH_SCALE;
                canvas.save();
                canvas.translate(
                        (mWidth - bounds.width() * scale) / 2f + mParallaxX,
                        (mHeight - bounds.height() * scale) / 2f);
                canvas.scale(scale, scale);
                mDrawable.setBounds(0, 0, bounds.width(), bounds.height());
                mDrawable.setFrame((int) mFrame);
                mDrawable.draw(canvas);
                canvas.restore();

                // Gentle edge falloff makes the custom scenes feel less flat while leaving
                // character colors and shapes untouched.
                if (mVignettePaint.getShader() != null) {
                    canvas.drawRect(0, 0, mWidth, mHeight, mVignettePaint);
                }
            } catch (IllegalStateException | IllegalArgumentException e) {
                // Surface disappeared between frames.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (IllegalStateException | IllegalArgumentException e) {
                        // Ignore a surface teardown race.
                    }
                }
            }
        }
    }
}
