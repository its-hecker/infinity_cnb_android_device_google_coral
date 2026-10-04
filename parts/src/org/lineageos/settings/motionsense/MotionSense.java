/*
 * SPDX-FileCopyrightText: 2026 its-hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.motionsense;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.SystemProperties;
import android.provider.Settings;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;

import com.android.settingslib.widget.IllustrationPreference;

import org.lineageos.settings.R;

/** Settings keys and shared helpers for the Motion Sense pages. */
final class MotionSense {

    // Settings.Secure, read by OsloFeedback (com.google.oslo) or SystemUI
    static final String KEY_ENABLED = "aware_enabled";
    static final String KEY_SKIP = "skip_gesture";
    static final String KEY_SKIP_DIRECTION = "skip_gesture_direction";
    static final String KEY_SILENCE = "silence_gesture";
    static final String KEY_TAP = "tap_gesture";
    static final String KEY_WAKE_DISPLAY = "doze_wake_display_gesture";
    static final String KEY_ALWAYS_ON = "doze_always_on";
    static final String KEY_WAKE_SCREEN = "doze_wake_screen_gesture";
    static final String KEY_LOCK = "aware_lock_enabled";
    static final String KEY_ANY_MEDIA_APP = "aware_any_media_app";
    static final String KEY_IGNORE_VIDEOS = "aware_ignore_videos";
    static final String KEY_GLOW_CUSTOM = "aware_glow_custom";
    static final String KEY_GLOW_HUE = "aware_glow_hue";
    static final String KEY_GLOW_SHOW = "aware_glow_show";
    static final String KEY_GLOW_BRIGHTNESS = "aware_glow_brightness";
    static final String KEY_GLOW_SIZE = "aware_glow_size";
    static final String KEY_GLOW_RAINBOW = "aware_glow_rainbow";
    static final String KEY_GLOW_ACCENT = "aware_glow_accent";
    static final String KEY_GLOW_NIGHT = "aware_glow_night";
    static final String KEY_GLOW_NIGHT_START = "aware_glow_night_start";
    static final String KEY_MEDIA_APPS = "aware_media_apps";

    static final int DEFAULT_GLOW_BRIGHTNESS = 100;
    static final int DEFAULT_GLOW_SIZE = 100;
    static final int DEFAULT_GLOW_NIGHT_START = 22;

    // Settings.Global
    static final String KEY_ALLOWED = "aware_allowed";
    static final String KEY_AIRPLANE = Settings.Global.AIRPLANE_MODE_ON;
    static final String KEY_LOW_POWER = "low_power";

    static final int DEFAULT_GLOW_HUE = 270;

    /** Every Secure key the pages show, for content observers. */
    static final String[] SECURE_KEYS = {
        KEY_ENABLED, KEY_SKIP, KEY_SKIP_DIRECTION, KEY_SILENCE, KEY_TAP, KEY_WAKE_DISPLAY,
        KEY_ALWAYS_ON, KEY_WAKE_SCREEN, KEY_LOCK, KEY_ANY_MEDIA_APP, KEY_IGNORE_VIDEOS,
        KEY_GLOW_CUSTOM, KEY_GLOW_HUE, KEY_GLOW_SHOW, KEY_GLOW_BRIGHTNESS, KEY_GLOW_SIZE,
        KEY_GLOW_RAINBOW, KEY_GLOW_ACCENT, KEY_GLOW_NIGHT, KEY_GLOW_NIGHT_START, KEY_MEDIA_APPS,
    };
    static final String[] GLOBAL_KEYS = {KEY_ALLOWED, KEY_AIRPLANE, KEY_LOW_POWER};

    /** Remembers which features the user turned off, so turning Motion Sense on keeps them off. */
    private static final String PREFS_NAME = "motion_sense";

    private MotionSense() {}

    static int getDefault(String key) {
        switch (key) {
            case KEY_ENABLED:
            case KEY_TAP:
            case KEY_SKIP_DIRECTION:
            case KEY_GLOW_RAINBOW:
            case KEY_GLOW_ACCENT:
            case KEY_GLOW_NIGHT:
                return 0;
            case KEY_GLOW_HUE:
                return DEFAULT_GLOW_HUE;
            case KEY_GLOW_BRIGHTNESS:
                return DEFAULT_GLOW_BRIGHTNESS;
            case KEY_GLOW_SIZE:
                return DEFAULT_GLOW_SIZE;
            case KEY_GLOW_NIGHT_START:
                return DEFAULT_GLOW_NIGHT_START;
            default:
                return 1;
        }
    }

    static int get(Context context, String key) {
        return Settings.Secure.getInt(context.getContentResolver(), key, getDefault(key));
    }

    static boolean isOn(Context context, String key) {
        return get(context, key) != 0;
    }

    static void put(Context context, String key, int value) {
        Settings.Secure.putInt(context.getContentResolver(), key, value);
    }

    static boolean isSupported(Context context) {
        return SystemProperties.getBoolean("ro.vendor.aware_available", false)
                && Settings.Global.getInt(context.getContentResolver(), KEY_ALLOWED, 0) == 1;
    }

    static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), KEY_AIRPLANE, 0) == 1;
    }

    static boolean isBatterySaverOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), KEY_LOW_POWER, 0) == 1;
    }

    static boolean isAvailable(Context context) {
        return isSupported(context) && !isAirplaneModeOn(context) && !isBatterySaverOn(context);
    }

    /** True when gestures can be changed: Motion Sense is on and available. */
    static boolean isConfigurable(Context context) {
        return isAvailable(context) && isOn(context, KEY_ENABLED);
    }

    /** Why Motion Sense is unavailable, or 0 when it is available. */
    static int getUnavailableReason(Context context) {
        if (!isSupported(context)) {
            return R.string.motion_sense_not_allowed;
        }
        boolean airplane = isAirplaneModeOn(context);
        boolean saver = isBatterySaverOn(context);
        if (airplane && saver) {
            return R.string.motion_sense_unavailable_airplane_saver;
        } else if (airplane) {
            return R.string.motion_sense_unavailable_airplane;
        } else if (saver) {
            return R.string.motion_sense_unavailable_saver;
        }
        return 0;
    }

    static void registerObserver(ContentResolver resolver, ContentObserver observer) {
        for (String key : SECURE_KEYS) {
            resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer);
        }
        for (String key : GLOBAL_KEYS) {
            resolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer);
        }
    }

    private static SharedPreferences getFeaturePrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static void rememberFeature(Context context, String key, boolean on) {
        getFeaturePrefs(context).edit().putBoolean(key, on).apply();
    }

    /**
     * Pixel's Settings turns every Motion Sense feature on when Motion Sense is turned on,
     * except those the user turned off before.
     */
    static void restoreFeatures(Context context) {
        SharedPreferences prefs = getFeaturePrefs(context);
        for (String key : new String[] {
                KEY_SKIP, KEY_SILENCE, KEY_TAP, KEY_WAKE_SCREEN, KEY_LOCK,
                KEY_WAKE_DISPLAY, KEY_ALWAYS_ON}) {
            if (prefs.getBoolean(key, true)) {
                put(context, key, 1);
            }
        }
    }

    /** The glow color Oslo shows, close enough for the illustrations. */
    static int getGlowColor(Context context) {
        if (!isOn(context, KEY_GLOW_CUSTOM)) {
            return Color.HSVToColor(new float[] {215f, 0.5f, 1f});
        }
        return Color.HSVToColor(new float[] {get(context, KEY_GLOW_HUE) % 360, 0.55f, 1f});
    }

    /**
     * Shows a Motion Sense animation in Material You colors, with the glow in the
     * user's glow color. Returns a holder for the bound view, so the glow can be
     * recolored when the glow setting changes.
     */
    static Illustration setUpIllustration(Context context, IllustrationPreference preference,
            int rawRes, int description) {
        Illustration illustration = new Illustration(context);
        preference.setLottieAnimationResId(rawRes);
        preference.setContentDescription(description);
        preference.applyIlloColors();
        preference.setOnBindListener(view -> {
            illustration.mView = view;
            illustration.mColor = 0; // a new view has no tint yet
            illustration.updateGlow();
        });
        return illustration;
    }

    static final class Illustration {
        private final Context mContext;
        private LottieAnimationView mView;
        private int mColor;

        Illustration(Context context) {
            mContext = context;
        }

        void updateGlow() {
            final int color = getGlowColor(mContext);
            if (mView == null || color == mColor) {
                return;
            }
            mColor = color;
            mView.addValueCallback(new KeyPath("**", ".motionGlow", "**"),
                    LottieProperty.COLOR_FILTER,
                    frameInfo -> new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
        }
    }
}
