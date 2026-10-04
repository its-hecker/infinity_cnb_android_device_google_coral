/*
 * SPDX-FileCopyrightText: 2026 its-hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.motionsense;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import org.lineageos.settings.R;

/** The Glow light settings page (brightness, size, color). */
public class GlowActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG_GLOW = "glow";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.motion_sense_glow_page_title);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new GlowFragment(), TAG_GLOW)
                    .commit();
        }
    }
}
