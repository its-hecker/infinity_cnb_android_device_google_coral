/*
 * SPDX-FileCopyrightText: 2026 Hecker
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hecker.motionsense.wallpapers;

/** Limit work to an even number of display vsyncs without losing animation elapsed time. */
final class SidekickFramePacer {
    private float refreshRate = 60f;
    private long nextDrawNs;
    private long intervalNs;
    private long lastDrawNs;
    private boolean started;

    void setRefreshRate(float hz) {
        refreshRate = Float.isFinite(hz) && hz >= 30f && hz <= 240f ? hz : 60f;
        reset();
    }

    void reset() {
        started = false;
        nextDrawNs = 0;
        intervalNs = 0;
    }

    boolean shouldDraw(long nowNs, boolean idle) {
        float desired = idle ? 30f : 60f;
        int vsyncs = Math.max(1, (int) Math.ceil(refreshRate / desired - .01f));
        long interval = Math.round(vsyncs * 1_000_000_000d / refreshRate);
        if (started && nowNs <= lastDrawNs) {
            return false;
        }
        if (!started || interval != intervalNs) {
            started = true;
            intervalNs = interval;
            nextDrawNs = nowNs + interval;
            lastDrawNs = nowNs;
            return true;
        }
        // Allow small timestamp rounding, not an extra draw within the same vsync.
        if (nowNs + 500_000L < nextDrawNs) {
            return false;
        }
        nextDrawNs += interval;
        if (nextDrawNs <= nowNs) {
            nextDrawNs = nowNs + interval;
        }
        lastDrawNs = nowNs;
        return true;
    }
}
