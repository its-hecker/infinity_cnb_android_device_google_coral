/*
 * SPDX-FileCopyrightText: 2026 its-hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.motionsense;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.hardware.display.AmbientDisplayConfiguration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

/**
 * Motion Sense settings, shown in Settings > System through tile injection, so the
 * ROM's Settings app needs no changes. Every option is a Settings.Secure value that
 * OsloFeedback (com.google.oslo) or SystemUI reads.
 */
public class MotionSenseFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    // Settings.Secure
    private static final String KEY_ENABLED = "aware_enabled";
    private static final String KEY_SKIP = "skip_gesture";
    private static final String KEY_SKIP_DIRECTION = "skip_gesture_direction";
    private static final String KEY_SILENCE = "silence_gesture";
    private static final String KEY_TAP = "tap_gesture";
    private static final String KEY_WAKE_DISPLAY = "doze_wake_display_gesture";
    private static final String KEY_ALWAYS_ON = "doze_always_on";
    private static final String KEY_WAKE_SCREEN = "doze_wake_screen_gesture";
    private static final String KEY_LOCK = "aware_lock_enabled";
    private static final String KEY_ANY_MEDIA_APP = "aware_any_media_app";
    private static final String KEY_IGNORE_VIDEOS = "aware_ignore_videos";
    private static final String KEY_GLOW_CUSTOM = "aware_glow_custom";
    private static final String KEY_GLOW_HUE = "aware_glow_hue";

    // Settings.Global
    private static final String KEY_ALLOWED = "aware_allowed";
    private static final String KEY_AIRPLANE = Settings.Global.AIRPLANE_MODE_ON;
    private static final String KEY_LOW_POWER = "low_power";

    // Preference keys that are not settings
    private static final String PREF_IDLE = "idle_lock_screen";
    private static final String PREF_FOOTER = "motion_sense_footer";
    private static final String PREF_CATEGORY_GESTURES = "category_gestures";
    private static final String PREF_CATEGORY_SECURITY = "category_security";

    private static final String IDLE_NEARBY = "nearby";
    private static final String IDLE_ALWAYS = "always";
    private static final String IDLE_OFF = "off";

    private static final int DEFAULT_GLOW_HUE = 270;

    /** Remembers which features the user turned off, so turning Motion Sense on keeps them off. */
    private static final String PREFS_NAME = "motion_sense";

    /** The gesture switches and their defaults, as Pixel's Settings uses them. */
    private static final String[] FEATURE_KEYS = {
        KEY_SKIP, KEY_SILENCE, KEY_TAP, KEY_WAKE_SCREEN, KEY_LOCK,
    };
    private static final int[] FEATURE_DEFAULTS = {1, 1, 0, 1, 1};

    private ContentResolver mResolver;
    private AmbientDisplayConfiguration mAmbientConfig;
    private final int mUserId = UserHandle.myUserId();

    private MainSwitchPreference mMainSwitch;
    private ListPreference mSkipDirection;
    private ListPreference mIdleLockScreen;
    private SwitchPreferenceCompat mIgnoreVideos;
    private ListPreference mGlowHue;
    private FooterPreference mFooter;
    private PreferenceCategory mSecurityCategory;

    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateState();
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.motion_sense_settings, rootKey);

        Context context = requireContext();
        mResolver = context.getContentResolver();
        mAmbientConfig = new AmbientDisplayConfiguration(context);

        mMainSwitch = findPreference(KEY_ENABLED);
        mSkipDirection = findPreference(KEY_SKIP_DIRECTION);
        mIdleLockScreen = findPreference(PREF_IDLE);
        mIgnoreVideos = findPreference(KEY_IGNORE_VIDEOS);
        mGlowHue = findPreference(KEY_GLOW_HUE);
        mFooter = findPreference(PREF_FOOTER);
        mSecurityCategory = findPreference(PREF_CATEGORY_SECURITY);

        for (String key : new String[] {
                KEY_ENABLED, KEY_SKIP, KEY_SKIP_DIRECTION, KEY_SILENCE, KEY_TAP, PREF_IDLE,
                KEY_WAKE_SCREEN, KEY_LOCK, KEY_ANY_MEDIA_APP, KEY_IGNORE_VIDEOS,
                KEY_GLOW_CUSTOM, KEY_GLOW_HUE}) {
            Preference preference = findPreference(key);
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            }
        }

        // Pixel hides "Pause music" when Google turns tap off for a device.
        if (!DeviceConfig.getBoolean("oslo", "enable_tap", true)) {
            PreferenceCategory gestures = findPreference(PREF_CATEGORY_GESTURES);
            Preference tap = findPreference(KEY_TAP);
            if (gestures != null && tap != null) {
                gestures.removePreference(tap);
            }
        }

        // There is no always-on display option on devices without it.
        if (!mAmbientConfig.alwaysOnAvailableForUser(mUserId)) {
            mIdleLockScreen.setEntries(new CharSequence[] {
                    getString(R.string.motion_sense_idle_nearby),
                    getString(R.string.motion_sense_idle_off)});
            mIdleLockScreen.setEntryValues(new CharSequence[] {IDLE_NEARBY, IDLE_OFF});
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        for (String key : new String[] {
                KEY_ENABLED, KEY_SKIP, KEY_SKIP_DIRECTION, KEY_SILENCE, KEY_TAP,
                KEY_WAKE_DISPLAY, KEY_ALWAYS_ON, KEY_WAKE_SCREEN, KEY_LOCK,
                KEY_ANY_MEDIA_APP, KEY_IGNORE_VIDEOS, KEY_GLOW_CUSTOM, KEY_GLOW_HUE}) {
            mResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, mObserver);
        }
        for (String key : new String[] {KEY_ALLOWED, KEY_AIRPLANE, KEY_LOW_POWER}) {
            mResolver.registerContentObserver(Settings.Global.getUriFor(key), false, mObserver);
        }
        updateState();
    }

    @Override
    public void onPause() {
        super.onPause();
        mResolver.unregisterContentObserver(mObserver);
    }

    private boolean isSupported() {
        return SystemProperties.getBoolean("ro.vendor.aware_available", false)
                && Settings.Global.getInt(mResolver, KEY_ALLOWED, 0) == 1;
    }

    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(mResolver, KEY_AIRPLANE, 0) == 1;
    }

    private boolean isBatterySaverOn() {
        return Settings.Global.getInt(mResolver, KEY_LOW_POWER, 0) == 1;
    }

    private boolean isEnabled() {
        return getSecure(KEY_ENABLED, 0) == 1;
    }

    private int getSecure(String key, int def) {
        return Settings.Secure.getInt(mResolver, key, def);
    }

    private void putSecure(String key, int value) {
        Settings.Secure.putInt(mResolver, key, value);
    }

    private int defaultFor(String key) {
        for (int i = 0; i < FEATURE_KEYS.length; i++) {
            if (FEATURE_KEYS[i].equals(key)) {
                return FEATURE_DEFAULTS[i];
            }
        }
        return 1;
    }

    private void updateState() {
        if (getContext() == null) {
            return;
        }
        final boolean supported = isSupported();
        final boolean airplane = isAirplaneModeOn();
        final boolean saver = isBatterySaverOn();
        final boolean available = supported && !airplane && !saver;
        final boolean enabled = isEnabled();
        final boolean configurable = available && enabled;

        mMainSwitch.setChecked(enabled);
        mMainSwitch.setEnabled(available);

        for (String key : FEATURE_KEYS) {
            SwitchPreferenceCompat preference = findPreference(key);
            if (preference != null) {
                preference.setChecked(getSecure(key, defaultFor(key)) == 1);
                preference.setEnabled(configurable);
            }
        }

        boolean rtl = getSecure(KEY_SKIP_DIRECTION, 0) == 0;
        mSkipDirection.setValue(rtl ? "0" : "1");
        mSkipDirection.setSummary(rtl ? R.string.motion_sense_skip_direction_rtl
                : R.string.motion_sense_skip_direction_ltr);
        mSkipDirection.setEnabled(configurable && getSecure(KEY_SKIP, 1) == 1);

        // Same logic as Pixel's "Idle lock screen" picker.
        boolean alwaysOn = mAmbientConfig.alwaysOnEnabled(mUserId);
        boolean wakeDisplay = mAmbientConfig.wakeDisplayGestureEnabled(mUserId);
        String idle = (wakeDisplay && configurable && alwaysOn) ? IDLE_NEARBY
                : alwaysOn ? IDLE_ALWAYS : IDLE_OFF;
        mIdleLockScreen.setValue(idle);
        mIdleLockScreen.setSummary(mIdleLockScreen.getEntry());

        // Auto-lock only makes sense with a PIN, pattern or password.
        KeyguardManager keyguard = requireContext().getSystemService(KeyguardManager.class);
        mSecurityCategory.setVisible(keyguard != null && keyguard.isDeviceSecure());

        // Extras, read by OsloFeedback
        SwitchPreferenceCompat anyMediaApp = findPreference(KEY_ANY_MEDIA_APP);
        anyMediaApp.setChecked(getSecure(KEY_ANY_MEDIA_APP, 1) == 1);
        mIgnoreVideos.setChecked(getSecure(KEY_IGNORE_VIDEOS, 1) == 1);
        mIgnoreVideos.setEnabled(anyMediaApp.isChecked());
        SwitchPreferenceCompat glowCustom = findPreference(KEY_GLOW_CUSTOM);
        glowCustom.setChecked(getSecure(KEY_GLOW_CUSTOM, 1) == 1);
        int hue = getSecure(KEY_GLOW_HUE, DEFAULT_GLOW_HUE);
        mGlowHue.setValue(String.valueOf(hue));
        CharSequence hueName = mGlowHue.getEntry();
        mGlowHue.setSummary(hueName != null ? hueName
                : getString(R.string.motion_sense_glow_color_custom, hue));
        mGlowHue.setEnabled(glowCustom.isChecked());

        if (!supported) {
            mFooter.setTitle(R.string.motion_sense_not_allowed);
        } else if (airplane && saver) {
            mFooter.setTitle(R.string.motion_sense_unavailable_airplane_saver);
        } else if (airplane) {
            mFooter.setTitle(R.string.motion_sense_unavailable_airplane);
        } else if (saver) {
            mFooter.setTitle(R.string.motion_sense_unavailable_saver);
        } else {
            mFooter.setTitle(R.string.motion_sense_footer);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        switch (key) {
            case KEY_ENABLED:
                if ((Boolean) newValue) {
                    putSecure(KEY_ENABLED, 1);
                    restoreFeatures();
                    updateState();
                    return true;
                }
                confirmTurnOff();
                return false;
            case KEY_SKIP_DIRECTION:
            case KEY_GLOW_HUE:
                try {
                    putSecure(key, Integer.parseInt((String) newValue));
                } catch (NumberFormatException e) {
                    return false;
                }
                break;
            case PREF_IDLE:
                setIdleLockScreen((String) newValue);
                break;
            default:
                boolean on = (Boolean) newValue;
                putSecure(key, on ? 1 : 0);
                rememberFeature(key, on);
                break;
        }
        updateState();
        return true;
    }

    private void confirmTurnOff() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.motion_sense_off_dialog_title)
                .setMessage(R.string.motion_sense_off_dialog_message)
                .setPositiveButton(R.string.motion_sense_off_dialog_confirm, (dialog, which) -> {
                    putSecure(KEY_ENABLED, 0);
                    updateState();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setIdleLockScreen(String value) {
        boolean alwaysOn = !IDLE_OFF.equals(value);
        boolean wakeDisplay = IDLE_NEARBY.equals(value);
        putSecure(KEY_ALWAYS_ON, alwaysOn ? 1 : 0);
        putSecure(KEY_WAKE_DISPLAY, wakeDisplay ? 1 : 0);
        rememberFeature(KEY_ALWAYS_ON, alwaysOn);
        rememberFeature(KEY_WAKE_DISPLAY, wakeDisplay);
    }

    private SharedPreferences getFeaturePrefs() {
        return requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void rememberFeature(String key, boolean on) {
        getFeaturePrefs().edit().putBoolean(key, on).apply();
    }

    /**
     * Pixel's Settings turns every Motion Sense feature on when Motion Sense is turned on,
     * except those the user turned off before.
     */
    private void restoreFeatures() {
        SharedPreferences prefs = getFeaturePrefs();
        for (String key : new String[] {
                KEY_SKIP, KEY_SILENCE, KEY_TAP, KEY_WAKE_SCREEN, KEY_LOCK,
                KEY_WAKE_DISPLAY, KEY_ALWAYS_ON}) {
            if (prefs.getBoolean(key, true)) {
                putSecure(key, 1);
            }
        }
    }
}
