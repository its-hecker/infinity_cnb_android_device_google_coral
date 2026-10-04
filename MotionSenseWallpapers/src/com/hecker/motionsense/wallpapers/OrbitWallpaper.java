/*
 * SPDX-FileCopyrightText: 2026 Hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hecker.motionsense.wallpapers;

public class OrbitWallpaper extends MotionSenseWallpaperService {
    @Override
    protected int getAnimation() {
        return R.raw.wallpaper_orbit;
    }
}
