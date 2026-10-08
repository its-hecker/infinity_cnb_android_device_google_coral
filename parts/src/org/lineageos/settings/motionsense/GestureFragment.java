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
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.IllustrationPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class GestureFragment extends SettingsBasePreferenceFragment {

    enum Gesture {
        SKIP(MotionSense.KEY_SKIP, R.string.motion_sense_skip_title,
                R.string.motion_sense_skip_summary, R.raw.motion_sense_skip),
        SILENCE(MotionSense.KEY_SILENCE, R.string.motion_sense_silence_title,
                R.string.motion_sense_silence_summary, R.raw.motion_sense_silence),
        TAP(MotionSense.KEY_TAP, R.string.motion_sense_tap_title,
                R.string.motion_sense_tap_summary, R.raw.motion_sense_tap),
        REACH(MotionSense.KEY_WAKE_SCREEN, R.string.motion_sense_reach_title,
                R.string.motion_sense_reach_summary, R.raw.motion_sense_reach);

        final String key;
        final int title;
        final int summary;
        final int animation;

        Gesture(String key, int title, int summary, int animation) {
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.animation = animation;
        }

        static Gesture fromKey(String key) {
            for (Gesture gesture : values()) {
                if (gesture.key.equals(key)) {
                    return gesture;
                }
            }
            return null;
        }
    }

    private Gesture mGesture;
    private MotionSense.Illustration mIllustration;
    private MainSwitchPreference mSwitch;
    private ListPreference mDirection;
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
        mGesture = Gesture.fromKey(requireArguments().getString(GestureActivity.EXTRA_GESTURE));
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);

        IllustrationPreference illustration = new IllustrationPreference(context);
        illustration.setKey("gesture_illustration");
        mIllustration = MotionSense.setUpIllustration(context, illustration,
                mGesture.animation, mGesture.title);
        screen.addPreference(illustration);

        mSwitch = new MainSwitchPreference(context);
        mSwitch.setKey(mGesture.key);
        mSwitch.setTitle(mGesture.title);
        mSwitch.setPersistent(false);
        mSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean on = (Boolean) newValue;
            MotionSense.put(context, mGesture.key, on ? 1 : 0);
            MotionSense.rememberFeature(context, mGesture.key, on);
            if (!on && (mGesture == Gesture.SKIP || mGesture == Gesture.TAP)) {
                MotionSense.put(context, MotionSense.KEY_AIR_DJ, 0);
            }
            return true;
        });
        screen.addPreference(mSwitch);

        if (mGesture == Gesture.SKIP) {
            mDirection = new ListPreference(context);
            mDirection.setKey(MotionSense.KEY_SKIP_DIRECTION);
            mDirection.setTitle(R.string.motion_sense_skip_direction_title);
            mDirection.setDialogTitle(R.string.motion_sense_skip_direction_title);
            mDirection.setEntries(R.array.motion_sense_skip_direction_entries);
            mDirection.setEntryValues(R.array.motion_sense_skip_direction_values);
            mDirection.setPersistent(false);
            mDirection.setOnPreferenceChangeListener((preference, newValue) -> {
                MotionSense.put(context, MotionSense.KEY_SKIP_DIRECTION,
                        Integer.parseInt((String) newValue));
                return true;
            });
            screen.addPreference(mDirection);
        }

        mFooter = new FooterPreference(context);
        mFooter.setKey("gesture_footer");
        mFooter.setSelectable(false);
        screen.addPreference(mFooter);
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
        boolean on = MotionSense.isOn(context, mGesture.key);
        mSwitch.setChecked(on);
        mSwitch.setEnabled(configurable);

        if (mDirection != null) {
            boolean rtl = MotionSense.get(context, MotionSense.KEY_SKIP_DIRECTION) == 0;
            mDirection.setValue(rtl ? "0" : "1");
            mDirection.setSummary(rtl ? R.string.motion_sense_skip_direction_rtl
                    : R.string.motion_sense_skip_direction_ltr);
            mDirection.setEnabled(configurable && on);
        }

        int reason = MotionSense.getUnavailableReason(context);
        if (reason != 0) {
            mFooter.setTitle(reason);
        } else if (!MotionSense.isOn(context, MotionSense.KEY_ENABLED)) {
            mFooter.setTitle(R.string.motion_sense_turn_on_first);
        } else {
            mFooter.setTitle(MotionSense.isOn(context, MotionSense.KEY_AIR_DJ)
                    && (mGesture == Gesture.SKIP || mGesture == Gesture.TAP)
                    ? R.string.motion_sense_air_dj_summary : mGesture.summary);
        }
        mIllustration.updateGlow();
    }
}
