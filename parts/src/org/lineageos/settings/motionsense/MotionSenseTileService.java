/*
 * SPDX-FileCopyrightText: 2026 its-hecker
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.motionsense;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile that toggles Motion Sense on and off. */
public class MotionSenseTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!MotionSense.isAvailable(this)) {
            return;
        }
        boolean on = MotionSense.isOn(this, MotionSense.KEY_ENABLED);
        MotionSense.put(this, MotionSense.KEY_ENABLED, on ? 0 : 1);
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        boolean available = MotionSense.isAvailable(this);
        boolean on = MotionSense.isOn(this, MotionSense.KEY_ENABLED);
        tile.setState(!available ? Tile.STATE_UNAVAILABLE
                : on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
