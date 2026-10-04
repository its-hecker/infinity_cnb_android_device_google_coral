/*
 * SPDX-FileCopyrightText: 2026 its-hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.motionsense;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.display.AmbientDisplayConfiguration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.IllustrationPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

import static org.lineageos.settings.motionsense.MotionSense.*;

/**
 * Motion Sense settings, shown in Settings > System through tile injection, so the
 * ROM's Settings app needs no changes. Every option is a Settings.Secure value that
 * OsloFeedback (com.google.oslo) or SystemUI reads.
 */
public class MotionSenseFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String PREF_ILLUSTRATION = "motion_sense_illustration";
    private static final String PREF_IDLE = "idle_lock_screen";
    private static final String PREF_FOOTER = "motion_sense_footer";
    private static final String PREF_CATEGORY_GESTURES = "category_gestures";
    private static final String PREF_CATEGORY_SECURITY = "category_security";

    private static final String IDLE_NEARBY = "nearby";
    private static final String IDLE_ALWAYS = "always";
    private static final String IDLE_OFF = "off";

    /** Rows that open a gesture page and have their own switch. */
    private static final String[] GESTURE_KEYS = {KEY_SKIP, KEY_SILENCE, KEY_TAP, KEY_WAKE_SCREEN};

    private AmbientDisplayConfiguration mAmbientConfig;
    private final int mUserId = UserHandle.myUserId();

    private MotionSense.Illustration mIllustration;
    private MainSwitchPreference mMainSwitch;
    private ListPreference mIdleLockScreen;
    private SwitchPreferenceCompat mLock;
    private SwitchPreferenceCompat mAnyMediaApp;
    private Preference mMediaApps;
    private SwitchPreferenceCompat mIgnoreVideos;
    private SwitchPreferenceCompat mGlowCustom;
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
        mAmbientConfig = new AmbientDisplayConfiguration(context);

        IllustrationPreference illustration = findPreference(PREF_ILLUSTRATION);
        mIllustration = MotionSense.setUpIllustration(context, illustration,
                R.raw.motion_sense_main, R.string.motion_sense_title);

        mMainSwitch = findPreference(KEY_ENABLED);
        mIdleLockScreen = findPreference(PREF_IDLE);
        mLock = findPreference(KEY_LOCK);
        mAnyMediaApp = findPreference(KEY_ANY_MEDIA_APP);
        mMediaApps = findPreference(KEY_MEDIA_APPS);
        mIgnoreVideos = findPreference(KEY_IGNORE_VIDEOS);
        mGlowCustom = findPreference(KEY_GLOW_CUSTOM);
        mGlowHue = findPreference(KEY_GLOW_HUE);
        mFooter = findPreference(PREF_FOOTER);
        mSecurityCategory = findPreference(PREF_CATEGORY_SECURITY);

        for (Preference preference : new Preference[] {mMainSwitch, mIdleLockScreen, mLock,
                mAnyMediaApp, mIgnoreVideos, mGlowCustom, mGlowHue}) {
            preference.setOnPreferenceChangeListener(this);
        }
        for (String key : GESTURE_KEYS) {
            Preference row = findPreference(key);
            row.setOnPreferenceChangeListener(this);
            row.setIntent(new Intent(context, GestureActivity.class)
                    .putExtra(GestureActivity.EXTRA_GESTURE, key));
        }
        mMediaApps.setIntent(new Intent(context, MediaAppsActivity.class));

        // About: tap a donation address to copy it
        findPreference("about_binance").setOnPreferenceClickListener(p -> {
            copy(getString(R.string.motion_sense_binance_id));
            return true;
        });
        findPreference("about_usdt").setOnPreferenceClickListener(p -> {
            copy(getString(R.string.motion_sense_usdt_trc20));
            return true;
        });

        // Pixel hides "Pause music" when Google turns tap off for a device.
        if (!DeviceConfig.getBoolean("oslo", "enable_tap", true)) {
            PreferenceCategory gestures = findPreference(PREF_CATEGORY_GESTURES);
            gestures.removePreference(findPreference(KEY_TAP));
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
        MotionSense.registerObserver(requireContext().getContentResolver(), mObserver);
        updateState();
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().getContentResolver().unregisterContentObserver(mObserver);
    }

    private void updateState() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        final boolean available = isAvailable(context);
        final boolean enabled = isOn(context, KEY_ENABLED);
        final boolean configurable = available && enabled;

        mMainSwitch.setChecked(enabled);
        mMainSwitch.setEnabled(available);

        for (String key : GESTURE_KEYS) {
            PrimarySwitchPreference row = findPreference(key);
            if (row != null) {
                row.setChecked(isOn(context, key));
                row.setSwitchEnabled(configurable);
            }
        }

        // Same logic as Pixel's "Idle lock screen" picker.
        boolean alwaysOn = mAmbientConfig.alwaysOnEnabled(mUserId);
        boolean wakeDisplay = mAmbientConfig.wakeDisplayGestureEnabled(mUserId);
        String idle = (wakeDisplay && configurable && alwaysOn) ? IDLE_NEARBY
                : alwaysOn ? IDLE_ALWAYS : IDLE_OFF;
        mIdleLockScreen.setValue(idle);
        mIdleLockScreen.setSummary(mIdleLockScreen.getEntry());

        // Auto-lock only makes sense with a PIN, pattern or password.
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        mSecurityCategory.setVisible(keyguard != null && keyguard.isDeviceSecure());
        mLock.setChecked(isOn(context, KEY_LOCK));
        mLock.setEnabled(configurable);

        // Extras, read by OsloFeedback
        mAnyMediaApp.setChecked(isOn(context, KEY_ANY_MEDIA_APP));
        int listed = MediaApps.countInstalled(context);
        mMediaApps.setSummary(getResources().getQuantityString(
                R.plurals.motion_sense_media_apps_summary, listed, listed));
        mIgnoreVideos.setChecked(isOn(context, KEY_IGNORE_VIDEOS));
        mIgnoreVideos.setEnabled(mAnyMediaApp.isChecked());
        mGlowCustom.setChecked(isOn(context, KEY_GLOW_CUSTOM));
        int hue = get(context, KEY_GLOW_HUE);
        mGlowHue.setValue(String.valueOf(hue));
        CharSequence hueName = mGlowHue.getEntry();
        mGlowHue.setSummary(hueName != null ? hueName
                : getString(R.string.motion_sense_glow_color_custom, hue));
        mGlowHue.setEnabled(mGlowCustom.isChecked());

        int reason = getUnavailableReason(context);
        mFooter.setTitle(reason != 0 ? reason : R.string.motion_sense_footer);

        mIllustration.updateGlow();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = requireContext();
        final String key = preference.getKey();
        switch (key) {
            case KEY_ENABLED:
                if ((Boolean) newValue) {
                    put(context, KEY_ENABLED, 1);
                    restoreFeatures(context);
                    updateState();
                    return true;
                }
                confirmTurnOff();
                return false;
            case KEY_GLOW_HUE:
                try {
                    put(context, key, Integer.parseInt((String) newValue));
                } catch (NumberFormatException e) {
                    return false;
                }
                break;
            case PREF_IDLE:
                setIdleLockScreen((String) newValue);
                break;
            default:
                boolean on = (Boolean) newValue;
                put(context, key, on ? 1 : 0);
                rememberFeature(context, key, on);
                break;
        }
        updateState();
        return true;
    }

    private void copy(String text) {
        ClipboardManager clipboard = requireContext().getSystemService(ClipboardManager.class);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(text, text));
            Toast.makeText(requireContext(), R.string.motion_sense_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmTurnOff() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.motion_sense_off_dialog_title)
                .setMessage(R.string.motion_sense_off_dialog_message)
                .setPositiveButton(R.string.motion_sense_off_dialog_confirm, (dialog, which) -> {
                    put(requireContext(), KEY_ENABLED, 0);
                    updateState();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setIdleLockScreen(String value) {
        Context context = requireContext();
        boolean alwaysOn = !IDLE_OFF.equals(value);
        boolean wakeDisplay = IDLE_NEARBY.equals(value);
        put(context, KEY_ALWAYS_ON, alwaysOn ? 1 : 0);
        put(context, KEY_WAKE_DISPLAY, wakeDisplay ? 1 : 0);
        rememberFeature(context, KEY_ALWAYS_ON, alwaysOn);
        rememberFeature(context, KEY_WAKE_DISPLAY, wakeDisplay);
    }
}
