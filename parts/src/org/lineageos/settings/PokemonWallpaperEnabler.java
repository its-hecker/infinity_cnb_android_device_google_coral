/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.util.Log;

/** Clears the old split installer's disabled state once after Pokemon becomes bundled. */
final class PokemonWallpaperEnabler {
    private static final String TAG = "GoogleParts";
    private static final String PACKAGE = "com.google.pixel.livewallpaper";
    private static final String SERVICE = PACKAGE + ".pokemon.wallpapers.PokemonWallpaper";
    private static final String MIGRATED = "google_pokemon_bundled_v1";

    static void migrate(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                "wallpaper_migrations", Context.MODE_PRIVATE);
        if (preferences.getBoolean(MIGRATED, false)) {
            return;
        }
        PackageManager pm = context.getPackageManager();
        ComponentName component = new ComponentName(PACKAGE, SERVICE);
        try {
            ServiceInfo info = pm.getServiceInfo(component,
                    PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS);
            // Wait for the paired vendor fix. The original split manager would disable it again.
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    || !"android.permission.BIND_WALLPAPER".equals(info.permission)
                    || info.metaData == null
                    || info.metaData.containsKey("com.google.pixel.livewallpaper.module")
                    || info.metaData.containsKey("action_delete_live_wallpaper")) {
                return;
            }
            int state = pm.getComponentEnabledSetting(component);
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                // Restore the new enabled manifest default, rather than a permanent override.
                pm.setComponentEnabledSetting(component,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP);
                Log.i(TAG, "Restored bundled Google Pokemon Sidekick wallpaper");
            }
            preferences.edit().putBoolean(MIGRATED, true).apply();
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Google Pokemon wallpaper is not installed yet");
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "Could not restore bundled Google Pokemon wallpaper", e);
        }
    }

    private PokemonWallpaperEnabler() {}
}
