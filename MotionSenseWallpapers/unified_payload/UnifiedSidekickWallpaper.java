/*
 * SPDX-FileCopyrightText: 2026 Hecker
 * SPDX-License-Identifier: Apache-2.0
 *
 * Resource-independent Sidekick payload used by the unified PixelLiveWallpaper build.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * One live-wallpaper component for the complete Sidekick collection.
 *
 * Assets live under assets/sidekick and are discovered at runtime, so this payload can be
 * injected into Google's PixelLiveWallpaper APK without depending on either app's R table.
 * Google's original Pokemon renderer and Pokemon art stay untouched in the same APK.
 */
public final class UnifiedSidekickWallpaper extends WallpaperService {
    private static final String ASSET_DIR = "sidekick";
    private static final String PREFS = "sidekick_unified";
    private static final String PREF_CHARACTER = "selected_character";

    private static final class CharacterSpec {
        final String file;
        final String name;
        final float left;
        final float top;
        final float right;
        final float bottom;
        final float depth;
        final float tilt;
        final int glow;
        final String[] ambient;

        CharacterSpec(String file, String name, float left, float top, float right, float bottom,
                float depth, float tilt, int glow, String... ambient) {
            this.file = file;
            this.name = name;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.depth = depth;
            this.tilt = tilt;
            this.glow = glow;
            this.ambient = ambient;
        }

        boolean hit(float x, float y, int width, int height) {
            if (width <= 0 || height <= 0) {
                return false;
            }
            float nx = x / width;
            float ny = y / height;
            return nx >= left && nx <= right && ny >= top && ny <= bottom;
        }
    }

    /*
     * The last four are optional third-party fan-art packs generated/added by the faceid build.
     * If an asset is absent it is simply skipped; this keeps the payload safe for stripped builds.
     */
    private static final CharacterSpec[] CATALOG = {
            new CharacterSpec("wallpaper_hecker.json", "Hecker",
                    .16f, .27f, .84f, .98f, .035f, 2.4f, 0xff8b7cf6,
                    "look_left", "look_right", "nod", "wave"),
            new CharacterSpec("wallpaper_mochi.json", "Mochi",
                    .13f, .29f, .87f, .98f, .032f, 2.0f, 0xffb8a7ff,
                    "look_left", "look_right", "wave", "nod"),
            new CharacterSpec("wallpaper_biscuit.json", "Biscuit",
                    .12f, .34f, .88f, .99f, .034f, 2.2f, 0xffffb45a,
                    "look_left", "look_right", "nod", "wave"),
            new CharacterSpec("wallpaper_whisk_crumb.json", "Whisk & Crumb",
                    .08f, .35f, .92f, .99f, .030f, 1.8f, 0xffffca8c,
                    "look_left", "look_right", "nod", "wave"),
            new CharacterSpec("wallpaper_pip.json", "Pip",
                    .18f, .27f, .82f, .93f, .040f, 3.0f, 0xff57e4ff,
                    "look_left", "look_right", "wave", "nod"),
            new CharacterSpec("wallpaper_drift.json", "Drift",
                    .04f, .20f, .96f, .91f, .045f, 1.5f, 0xff70d8ff,
                    "left", "right", "wave", "nod"),
            new CharacterSpec("wallpaper_orbit.json", "Orbit",
                    .05f, .18f, .95f, .90f, .050f, 2.0f, 0xffa892ff,
                    "left", "right", "wave", "nod"),
            new CharacterSpec("wallpaper_aurora.json", "Aurora",
                    .03f, .16f, .97f, .90f, .055f, 1.2f, 0xff80e8d6,
                    "left", "right", "nod", "wave"),
            new CharacterSpec("wallpaper_doraemon.json", "Doraemon",
                    .13f, .27f, .87f, .99f, .035f, 2.2f, 0xff44a9ff,
                    "look_left", "look_right", "wave", "nod"),
            new CharacterSpec("wallpaper_ben10.json", "Ben 10",
                    .16f, .22f, .84f, .99f, .038f, 2.8f, 0xff69f35a,
                    "look_left", "look_right", "special", "wave"),
            new CharacterSpec("wallpaper_batman.json", "Batman",
                    .13f, .20f, .87f, .99f, .030f, 1.7f, 0xff8796b3,
                    "look_left", "look_right", "special", "nod"),
            new CharacterSpec("wallpaper_tom_jerry.json", "Tom & Jerry",
                    .07f, .31f, .93f, .99f, .034f, 2.5f, 0xffffb473,
                    "look_left", "look_right", "special", "wave"),
    };

    @Override
    public Engine onCreateEngine() {
        return new SidekickEngine();
    }

    private final class SidekickEngine extends Engine implements Choreographer.FrameCallback {
        private static final long SLEEP_MS = 24_000;
        private static final long AMBIENT_MS = 8_500;
        private static final long DOUBLE_TAP_MS = 315;
        private static final long LONG_PRESS_MS = 560;
        private static final long DEBOUNCE_MS = 330;
        private static final long IDLE_FRAME_NS = 33_000_000L;
        private static final float BASE_OVERSCAN = 1.04f;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final ArrayDeque<String> queue = new ArrayDeque<>();
        private final Paint vignette = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint particle = new Paint(Paint.ANTI_ALIAS_FLAG);

        private SharedPreferences prefs;
        private final List<CharacterSpec> available = new ArrayList<>();
        private CharacterSpec character;
        private int characterIndex;

        private LottieComposition composition;
        private LottieDrawable drawable;
        private OsloGestureClient oslo;

        private boolean visible;
        private int width;
        private int height;
        private String segment = "idle";
        private float segmentStart;
        private float segmentEnd;
        private float frame;
        private long lastFrameNs;
        private long lastDrawNs;
        private String lastReaction;
        private long lastReactionAt;

        private float offsetX = -1f;
        private float reactionOffsetX = -1f;
        private float parallaxX;
        private float parallaxY;
        private float targetParallaxX;
        private float targetParallaxY;
        private float sceneScale = 1f;
        private float targetSceneScale = 1f;
        private float sceneRotation;
        private float targetSceneRotation;
        private float glowEnergy;
        private float targetGlowEnergy;

        private boolean touchActive;
        private boolean downOnCharacter;
        private boolean longPressTriggered;
        private float downX;
        private float downY;
        private float touchThreshold;
        private long lastTapAt;
        private boolean lastTapOnCharacter;
        private int ambientStep;

        private final Runnable singleTap = () -> {
            if (lastTapAt == 0) {
                return;
            }
            boolean onCharacter = lastTapOnCharacter;
            lastTapAt = 0;
            if (onCharacter) {
                kick(1.018f, 0f, 0f, -height * .006f, .72f);
                react("tap", "wave", "nod");
            } else {
                kick(1.008f, 0f, 0f, 0f, .42f);
                react("nod", "wave");
            }
        };

        private final Runnable longPress = () -> {
            if (!visible || !touchActive) {
                return;
            }
            longPressTriggered = true;
            lastTapAt = 0;
            handler.removeCallbacks(singleTap);
            if (downOnCharacter) {
                kick(1.026f, 0f, 0f, -height * .010f, 1f);
                react("special", "pet", "nod", "wave");
            } else {
                kick(1.012f, 0f, 0f, 0f, .60f);
                react("wave", "nod");
            }
        };

        private final Runnable sleep = () -> {
            handler.removeCallbacks(ambient);
            playFirst("sleep", "asleep");
        };

        private final Runnable ambient = new Runnable() {
            @Override
            public void run() {
                if (!visible || isPreview() || isAsleep() || character == null) {
                    return;
                }
                if ("idle".equals(segment)) {
                    String desired = character.ambient[
                            ambientStep++ % Math.max(1, character.ambient.length)];
                    react(desired, "look_left", "look_right", "nod", "wave");
                    float sign = (ambientStep & 1) == 0 ? -1f : 1f;
                    kick(1.004f, sign * character.tilt * .20f,
                            sign * width * character.depth * .18f, 0f, .22f);
                }
                scheduleAmbient();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(true);

            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            touchThreshold = 46f * getResources().getDisplayMetrics().density;
            discoverCharacters();
            if (available.isEmpty()) {
                return;
            }

            String saved = prefs.getString(PREF_CHARACTER, null);
            characterIndex = indexOf(saved);
            if (characterIndex < 0) {
                characterIndex = 0;
            }
            load(available.get(characterIndex), "idle");

            oslo = new OsloGestureClient(UnifiedSidekickWallpaper.this,
                    new OsloGestureClient.Callback() {
                        @Override
                        public void onPresence() {
                            if (isAsleep()) {
                                kick(1.014f, 0f, 0f, -height * .008f, .52f);
                                react("wake", "wakeup");
                            } else {
                                react("look_left", "look_right", "nod");
                            }
                        }

                        @Override
                        public void onReach() {
                            kick(1.024f, 0f, 0f, -height * .012f, .92f);
                            if (isAsleep()) {
                                react("wake", "wakeup");
                            } else {
                                react("pet", "nod", "wave");
                            }
                        }

                        @Override
                        public void onFlick(int direction) {
                            directional(direction, 1.0f);
                        }

                        @Override
                        public void onSwipe(int direction) {
                            directional(direction, .72f);
                        }
                    });
        }

        private void discoverCharacters() {
            available.clear();
            for (CharacterSpec spec : CATALOG) {
                try (InputStream ignored = getAssets().open(ASSET_DIR + "/" + spec.file)) {
                    available.add(spec);
                } catch (IOException ignored) {
                    // Optional pack is absent.
                }
            }
        }

        private int indexOf(String file) {
            if (file == null) {
                return -1;
            }
            for (int i = 0; i < available.size(); i++) {
                if (file.equals(available.get(i).file)) {
                    return i;
                }
            }
            return -1;
        }

        private boolean load(CharacterSpec spec, String first) {
            LottieComposition next;
            try (InputStream in = getAssets().open(ASSET_DIR + "/" + spec.file)) {
                next = LottieCompositionFactory.fromJsonInputStreamSync(
                        in, "sidekick/" + spec.file).getValue();
            } catch (IOException | RuntimeException e) {
                return false;
            }
            if (next == null || next.getMarker("idle") == null) {
                return false;
            }

            character = spec;
            composition = next;
            drawable = new LottieDrawable();
            drawable.setComposition(next);
            queue.clear();
            playFirst(first, "idle");
            lastFrameNs = 0;
            lastDrawNs = 0;
            resetDynamics();
            draw();
            return true;
        }

        private void nextCharacter() {
            if (available.size() < 2) {
                return;
            }
            int old = characterIndex;
            for (int step = 1; step <= available.size(); step++) {
                int next = (old + step) % available.size();
                if (load(available.get(next), "wake")) {
                    characterIndex = next;
                    prefs.edit().putString(PREF_CHARACTER, character.file).apply();
                    lastReaction = null;
                    lastReactionAt = 0;
                    kick(1.045f, 0f, 0f, -height * .010f, 1f);
                    scheduleSleep();
                    scheduleAmbient();
                    return;
                }
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            width = w;
            height = h;
            float radius = Math.max(w, h) * .82f;
            vignette.setShader(new RadialGradient(
                    w * .5f, h * .43f, radius,
                    new int[] {Color.TRANSPARENT, Color.argb(8, 0, 0, 0),
                            Color.argb(54, 0, 0, 0)},
                    new float[] {0f, .66f, 1f}, Shader.TileMode.CLAMP));
            draw();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (visible) {
                lastFrameNs = 0;
                Choreographer.getInstance().postFrameCallback(this);
                if (!isPreview()) {
                    react("wake", "wakeup", "nod");
                    if (oslo != null) {
                        oslo.start();
                    }
                }
                scheduleSleep();
                scheduleAmbient();
            } else {
                stop();
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            stop();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            stop();
            if (oslo != null) {
                oslo.destroy();
                oslo = null;
            }
            handler.removeCallbacksAndMessages(null);
            super.onDestroy();
        }

        private void stop() {
            Choreographer.getInstance().removeFrameCallback(this);
            handler.removeCallbacks(sleep);
            handler.removeCallbacks(ambient);
            handler.removeCallbacks(singleTap);
            handler.removeCallbacks(longPress);
            touchActive = false;
            lastTapAt = 0;
            if (oslo != null) {
                oslo.stop();
            }
        }

        private boolean has(String marker) {
            return composition != null && composition.getMarker(marker) != null;
        }

        private String first(String... candidates) {
            for (String candidate : candidates) {
                if (candidate != null && has(candidate)) {
                    return candidate;
                }
            }
            return has("idle") ? "idle" : null;
        }

        private boolean play(String marker) {
            if (composition == null) {
                return false;
            }
            Marker m = composition.getMarker(marker);
            if (m == null) {
                return false;
            }
            segment = marker;
            segmentStart = m.startFrame;
            segmentEnd = m.startFrame + m.durationFrames;
            frame = segmentStart;
            return true;
        }

        private void playFirst(String... candidates) {
            String marker = first(candidates);
            if (marker != null) {
                play(marker);
            }
        }

        private boolean isAsleep() {
            return "sleep".equals(segment) || "asleep".equals(segment);
        }

        private void react(String... candidates) {
            String desired = first(candidates);
            if (desired == null) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (desired.equals(lastReaction) && now - lastReactionAt < DEBOUNCE_MS) {
                return;
            }
            lastReaction = desired;
            lastReactionAt = now;
            queue.clear();

            if (isAsleep() && !"sleep".equals(desired) && !"asleep".equals(desired)) {
                String wake = first("wakeup", "wake");
                if (wake != null && !wake.equals(desired)) {
                    play(wake);
                    queue.add(desired);
                } else {
                    play(desired);
                }
            } else {
                play(desired);
            }
            scheduleSleep();
            scheduleAmbient();
        }

        private void segmentFinished() {
            if (!queue.isEmpty()) {
                play(queue.poll());
            } else if ("sleep".equals(segment)) {
                playFirst("asleep", "idle");
            } else if ("asleep".equals(segment)) {
                playFirst("asleep", "idle");
            } else {
                playFirst("idle");
            }
        }

        private void scheduleSleep() {
            handler.removeCallbacks(sleep);
            if (visible && !isPreview()) {
                handler.postDelayed(sleep, SLEEP_MS);
            }
        }

        private void scheduleAmbient() {
            handler.removeCallbacks(ambient);
            if (visible && !isPreview() && !isAsleep()) {
                handler.postDelayed(ambient, AMBIENT_MS);
            }
        }

        private void directional(int direction, float force) {
            if (character == null) {
                return;
            }
            if (direction == 1 || direction == 2 || direction == 8) {
                kick(1.012f + .010f * force, character.tilt * force,
                        width * character.depth * force, 0f, .68f * force);
                react("right", "look_right", "wave", "nod");
            } else if (direction == 4 || direction == 5 || direction == 6) {
                kick(1.012f + .010f * force, -character.tilt * force,
                        -width * character.depth * force, 0f, .68f * force);
                react("left", "look_left", "wave", "nod");
            } else {
                kick(1.012f, 0f, 0f, -height * .005f, .45f);
                react("wave", "nod");
            }
        }

        private void kick(float scale, float rotation, float x, float y, float energy) {
            targetSceneScale = Math.max(targetSceneScale, scale);
            targetSceneRotation = rotation;
            targetParallaxX = x;
            targetParallaxY = y;
            targetGlowEnergy = Math.max(targetGlowEnergy, energy);
        }

        private void resetDynamics() {
            parallaxX = targetParallaxX = 0f;
            parallaxY = targetParallaxY = 0f;
            sceneScale = targetSceneScale = 1f;
            sceneRotation = targetSceneRotation = 0f;
            glowEnergy = targetGlowEnergy = 0f;
        }

        private void updateDynamics(float dt) {
            float fast = Math.min(1f, dt * 10f);
            float slow = Math.min(1f, dt * 4.8f);
            parallaxX += (targetParallaxX - parallaxX) * fast;
            parallaxY += (targetParallaxY - parallaxY) * fast;
            sceneScale += (targetSceneScale - sceneScale) * fast;
            sceneRotation += (targetSceneRotation - sceneRotation) * fast;
            glowEnergy += (targetGlowEnergy - glowEnergy) * fast;

            targetParallaxX += (0f - targetParallaxX) * slow;
            targetParallaxY += (0f - targetParallaxY) * slow;
            targetSceneScale += (1f - targetSceneScale) * slow;
            targetSceneRotation += (0f - targetSceneRotation) * slow;
            targetGlowEnergy += (0f - targetGlowEnergy) * slow;
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchActive = true;
                    longPressTriggered = false;
                    downX = event.getX();
                    downY = event.getY();
                    downOnCharacter = character != null
                            && character.hit(downX, downY, width, height);
                    handler.removeCallbacks(longPress);
                    handler.postDelayed(longPress, LONG_PRESS_MS);
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (touchActive) {
                        float dx = event.getX() - downX;
                        float dy = event.getY() - downY;
                        if (Math.hypot(dx, dy) > touchThreshold * .42f) {
                            handler.removeCallbacks(longPress);
                        }
                        if (downOnCharacter && width > 0 && height > 0) {
                            targetParallaxX = Math.max(-width * .035f,
                                    Math.min(width * .035f, dx * .10f));
                            targetParallaxY = Math.max(-height * .018f,
                                    Math.min(height * .018f, dy * .06f));
                            targetSceneRotation = Math.max(-2.2f,
                                    Math.min(2.2f, dx / width * 7f));
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPress);
                    if (!touchActive) {
                        break;
                    }
                    touchActive = false;

                    if (longPressTriggered) {
                        longPressTriggered = false;
                        break;
                    }

                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dx) >= touchThreshold || Math.abs(dy) >= touchThreshold) {
                        lastTapAt = 0;
                        handler.removeCallbacks(singleTap);
                        if (Math.abs(dx) > Math.abs(dy)) {
                            directional(dx > 0 ? 1 : 4, downOnCharacter ? 1f : .65f);
                        } else if (dy < 0) {
                            kick(1.025f, 0f, 0f, -height * .018f, .75f);
                            react("up", "wake", "nod", "wave");
                        } else {
                            kick(.992f, 0f, 0f, height * .008f, .25f);
                            react("sleep", "nod");
                        }
                        break;
                    }

                    boolean upOnCharacter = character != null
                            && character.hit(event.getX(), event.getY(), width, height);
                    boolean tapOnCharacter = downOnCharacter && upOnCharacter;
                    long now = SystemClock.uptimeMillis();

                    if (tapOnCharacter && lastTapAt != 0 && lastTapOnCharacter
                            && now - lastTapAt <= DOUBLE_TAP_MS) {
                        handler.removeCallbacks(singleTap);
                        lastTapAt = 0;
                        lastTapOnCharacter = false;
                        nextCharacter();
                    } else {
                        lastTapAt = now;
                        lastTapOnCharacter = tapOnCharacter;
                        handler.removeCallbacks(singleTap);
                        handler.postDelayed(singleTap, DOUBLE_TAP_MS);
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                    touchActive = false;
                    longPressTriggered = false;
                    handler.removeCallbacks(longPress);
                    break;

                default:
                    break;
            }
            super.onTouchEvent(event);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep,
                float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            if (character == null) {
                return;
            }
            if (offsetX >= 0) {
                float delta = xOffset - offsetX;
                if (Math.abs(delta) > .001f) {
                    targetParallaxX = Math.max(-width * character.depth,
                            Math.min(width * character.depth, delta * width * .32f));
                    targetSceneRotation = Math.max(-character.tilt,
                            Math.min(character.tilt, delta * 18f));
                }
            }

            if (reactionOffsetX < 0) {
                reactionOffsetX = xOffset;
            } else if (xOffsetStep > 0) {
                float delta = xOffset - reactionOffsetX;
                if (Math.abs(delta) >= xOffsetStep * .52f) {
                    directional(delta > 0 ? 1 : 4, .55f);
                    reactionOffsetX = xOffset;
                }
            }
            offsetX = xOffset;
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!visible) {
                return;
            }
            if (lastFrameNs != 0 && composition != null) {
                float dt = Math.min(.050f, (frameTimeNanos - lastFrameNs) / 1_000_000_000f);
                updateDynamics(dt);
                frame += dt * composition.getFrameRate();
                if (frame >= segmentEnd) {
                    segmentFinished();
                }
            }
            lastFrameNs = frameTimeNanos;

            boolean idle = "idle".equals(segment) || "asleep".equals(segment);
            boolean dynamics = Math.abs(targetParallaxX - parallaxX) > .2f
                    || Math.abs(targetParallaxY - parallaxY) > .2f
                    || Math.abs(sceneScale - 1f) > .001f
                    || Math.abs(sceneRotation) > .05f
                    || glowEnergy > .01f;
            if (!idle || dynamics || frameTimeNanos - lastDrawNs >= IDLE_FRAME_NS) {
                lastDrawNs = frameTimeNanos;
                draw();
            }
            Choreographer.getInstance().postFrameCallback(this);
        }

        private void draw() {
            if (composition == null || drawable == null || width == 0 || height == 0) {
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
                Rect bounds = composition.getBounds();
                float scale = Math.max(width / (float) bounds.width(),
                        height / (float) bounds.height()) * BASE_OVERSCAN * sceneScale;

                canvas.save();
                canvas.rotate(sceneRotation, width * .5f, height * .53f);
                canvas.translate((width - bounds.width() * scale) * .5f + parallaxX,
                        (height - bounds.height() * scale) * .5f + parallaxY);
                canvas.scale(scale, scale);
                drawable.setBounds(0, 0, bounds.width(), bounds.height());
                drawable.setFrame((int) frame);
                drawable.draw(canvas);
                canvas.restore();

                drawAtmosphere(canvas);

                if (vignette.getShader() != null) {
                    canvas.drawRect(0, 0, width, height, vignette);
                }
            } catch (IllegalStateException | IllegalArgumentException ignored) {
                // Surface can disappear between callbacks.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (IllegalStateException | IllegalArgumentException ignored) {
                    }
                }
            }
        }

        private void drawAtmosphere(Canvas canvas) {
            if (character == null) {
                return;
            }
            float cx = width * .5f + parallaxX * .35f;
            float cy = height * .53f + parallaxY * .25f;
            float radius = Math.max(width, height) * (.25f + glowEnergy * .06f);
            int alpha = Math.min(44, 8 + (int) (glowEnergy * 34f));
            int rgb = character.glow & 0x00ffffff;
            glow.setShader(new RadialGradient(cx, cy, radius,
                    new int[] {rgb | (alpha << 24), rgb | (alpha / 3 << 24), Color.TRANSPARENT},
                    new float[] {0f, .52f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, glow);
            glow.setShader(null);

            // Slow, low-alpha motes add depth without repainting or obscuring character artwork.
            long t = SystemClock.uptimeMillis();
            particle.setColor(character.glow);
            for (int i = 0; i < 8; i++) {
                float phase = ((t * (.000018f + i * .0000017f)) + i * .173f) % 1f;
                float x = ((i * 73) % 101) / 100f * width;
                float y = (1f - phase) * height;
                float drift = (float) Math.sin(phase * Math.PI * 2 + i) * width * .025f;
                particle.setAlpha(8 + (i % 3) * 4);
                canvas.drawCircle(x + drift, y, 1.3f + (i % 4) * .55f, particle);
            }
        }
    }
}
