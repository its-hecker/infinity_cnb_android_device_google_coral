/*
 * SPDX-FileCopyrightText: 2026 its-hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.motionsense;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/** One Motion Sense gesture: its animation, switch and options. */
public class GestureActivity extends CollapsingToolbarBaseActivity {

    static final String EXTRA_GESTURE = "gesture";
    private static final String TAG_GESTURE = "gesture";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GestureFragment.Gesture gesture =
                GestureFragment.Gesture.fromKey(getIntent().getStringExtra(EXTRA_GESTURE));
        if (gesture == null) {
            finish();
            return;
        }
        setTitle(gesture.title);

        if (savedInstanceState == null) {
            GestureFragment fragment = new GestureFragment();
            Bundle args = new Bundle();
            args.putString(EXTRA_GESTURE, gesture.key);
            fragment.setArguments(args);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            fragment, TAG_GESTURE)
                    .commit();
        }
    }
}
