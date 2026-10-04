/*
 * SPDX-FileCopyrightText: 2026 its-hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.motionsense;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.IllustrationPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

import static org.lineageos.settings.motionsense.MotionSense.*;

/**
 * The Glow light page: a master switch, brightness and size sliders, and the
 * color controls. Every value is a Settings.Secure key that OsloFeedback reads;
 * the glow redraws live (ContentObserver in OsloTweaks), no reboot.
 */
public class GlowFragment extends SettingsBasePreferenceFragment {

    private MotionSense.Illustration mIllustration;
    private MainSwitchPreference mShow;
    private SeekBarPreference mBrightness;
    private SeekBarPreference mSize;
    private SwitchPreferenceCompat mCustom;
    private ListPreference mColor;
    private SwitchPreferenceCompat mRainbow;
    private FooterPreference mFooter;

    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateState();
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = requireContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);

        IllustrationPreference illustration = new IllustrationPreference(context);
        illustration.setKey("glow_illustration");
        mIllustration = MotionSense.setUpIllustration(context, illustration,
                R.raw.motion_sense_glow, R.string.motion_sense_glow_page_title);
        screen.addPreference(illustration);

        mShow = new MainSwitchPreference(context);
        mShow.setKey(KEY_GLOW_SHOW);
        mShow.setTitle(R.string.motion_sense_glow_show_title);
        mShow.setPersistent(false);
        mShow.setOnPreferenceChangeListener((p, v) -> {
            put(context, KEY_GLOW_SHOW, (Boolean) v ? 1 : 0);
            return true;
        });
        screen.addPreference(mShow);

        mBrightness = newSlider(context, KEY_GLOW_BRIGHTNESS,
                R.string.motion_sense_glow_brightness_title, 10, 100);
        screen.addPreference(mBrightness);

        mSize = newSlider(context, KEY_GLOW_SIZE,
                R.string.motion_sense_glow_size_title, 50, 150);
        screen.addPreference(mSize);

        mCustom = new SwitchPreferenceCompat(context);
        mCustom.setKey(KEY_GLOW_CUSTOM);
        mCustom.setTitle(R.string.motion_sense_glow_custom_title);
        mCustom.setSummary(R.string.motion_sense_glow_custom_summary);
        mCustom.setPersistent(false);
        mCustom.setOnPreferenceChangeListener((p, v) -> {
            put(context, KEY_GLOW_CUSTOM, (Boolean) v ? 1 : 0);
            updateState();
            return true;
        });
        screen.addPreference(mCustom);

        mColor = new ListPreference(context);
        mColor.setKey(KEY_GLOW_HUE);
        mColor.setTitle(R.string.motion_sense_glow_color_title);
        mColor.setDialogTitle(R.string.motion_sense_glow_color_title);
        mColor.setEntries(R.array.motion_sense_glow_entries);
        mColor.setEntryValues(R.array.motion_sense_glow_values);
        mColor.setPersistent(false);
        mColor.setOnPreferenceChangeListener((p, v) -> {
            put(context, KEY_GLOW_HUE, Integer.parseInt((String) v));
            return true;
        });
        screen.addPreference(mColor);

        mRainbow = new SwitchPreferenceCompat(context);
        mRainbow.setKey(KEY_GLOW_RAINBOW);
        mRainbow.setTitle(R.string.motion_sense_glow_rainbow_title);
        mRainbow.setSummary(R.string.motion_sense_glow_rainbow_summary);
        mRainbow.setPersistent(false);
        mRainbow.setOnPreferenceChangeListener((p, v) -> {
            put(context, KEY_GLOW_RAINBOW, (Boolean) v ? 1 : 0);
            updateState();
            return true;
        });
        screen.addPreference(mRainbow);

        mFooter = new FooterPreference(context);
        mFooter.setKey("glow_footer");
        mFooter.setTitle(R.string.motion_sense_glow_footer);
        mFooter.setSelectable(false);
        screen.addPreference(mFooter);
    }

    private SeekBarPreference newSlider(Context context, String key, int title, int min, int max) {
        SeekBarPreference slider = new SeekBarPreference(context);
        slider.setKey(key);
        slider.setTitle(title);
        slider.setMin(min);
        slider.setMax(max);
        slider.setShowSeekBarValue(true);
        slider.setUpdatesContinuously(true);
        slider.setPersistent(false);
        slider.setOnPreferenceChangeListener((p, v) -> {
            put(context, key, (Integer) v);
            return true;
        });
        return slider;
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
        boolean configurable = MotionSense.isConfigurable(context);
        boolean show = isOn(context, KEY_GLOW_SHOW);
        boolean glowOn = configurable && show;
        boolean custom = isOn(context, KEY_GLOW_CUSTOM);
        boolean rainbow = isOn(context, KEY_GLOW_RAINBOW);

        mShow.setChecked(show);
        mShow.setEnabled(configurable);

        mBrightness.setValue(get(context, KEY_GLOW_BRIGHTNESS));
        mBrightness.setEnabled(glowOn);
        mSize.setValue(get(context, KEY_GLOW_SIZE));
        mSize.setEnabled(glowOn);

        mCustom.setChecked(custom);
        mCustom.setEnabled(glowOn);
        mRainbow.setChecked(rainbow);
        mRainbow.setEnabled(glowOn && custom);

        mColor.setValue(String.valueOf(get(context, KEY_GLOW_HUE)));
        CharSequence entry = mColor.getEntry();
        mColor.setSummary(entry != null ? entry
                : getString(R.string.motion_sense_glow_color_custom, get(context, KEY_GLOW_HUE)));
        mColor.setEnabled(glowOn && custom && !rainbow);

        int reason = MotionSense.getUnavailableReason(context);
        if (reason != 0) {
            mFooter.setTitle(reason);
        } else if (!MotionSense.isOn(context, KEY_ENABLED)) {
            mFooter.setTitle(R.string.motion_sense_turn_on_first);
        } else {
            mFooter.setTitle(R.string.motion_sense_glow_footer);
        }
        mIllustration.updateGlow();
    }
}
