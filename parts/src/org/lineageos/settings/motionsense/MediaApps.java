/*
 * SPDX-FileCopyrightText: 2026 its-hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.motionsense;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.Set;
import java.util.TreeSet;

/**
 * The media app list in Settings.Secure aware_media_apps. OsloFeedback falls back to
 * Google's list (DeviceConfig oslo/media_app_whitelist) when the key is unset.
 */
final class MediaApps {

    /** Google's default media_app_whitelist, from OsloDeviceConfig.getMediaAppList(). */
    static final String GOOGLE_LIST = "com.amazon.mp3,com.anghami,com.apple.android.music,"
            + "com.aspiro.tidal,com.bsbportal.music,com.clearchannel.iheartradio.controller,"
            + "com.ezpeer.ezpeerplus.v4,com.gaana,com.google.android.apps.youtube.music,"
            + "com.google.android.music,com.hungama.myplay.activity,com.jio.media.jiobeats,"
            + "com.ktmusic.geniemusic,com.neowiz.android.bugs,com.pandora.android,com.rhapsody,"
            + "com.shazam.android,com.sirius,com.skysoft.kkbox.android,com.spotify.music,"
            + "com.spotify.zerotap,deezer.android.app,fm.awa.liverpool";

    private MediaApps() {}

    static Set<String> get(Context context) {
        String list = Settings.Secure.getString(context.getContentResolver(),
                MotionSense.KEY_MEDIA_APPS);
        Set<String> apps = new TreeSet<>();
        for (String app : (list != null ? list : GOOGLE_LIST).split(",")) {
            if (!app.trim().isEmpty()) {
                apps.add(app.trim());
            }
        }
        return apps;
    }

    static void put(Context context, Set<String> apps) {
        Settings.Secure.putString(context.getContentResolver(), MotionSense.KEY_MEDIA_APPS,
                TextUtils.join(",", new TreeSet<>(apps)));
    }

    /** Goes back to Google's list. */
    static void reset(Context context) {
        Settings.Secure.putString(context.getContentResolver(), MotionSense.KEY_MEDIA_APPS, null);
    }

    /** How many apps on the list are installed. */
    static int countInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        int count = 0;
        for (String app : get(context)) {
            try {
                pm.getApplicationInfo(app, 0);
                count++;
            } catch (PackageManager.NameNotFoundException e) {
                // not installed
            }
        }
        return count;
    }
}
