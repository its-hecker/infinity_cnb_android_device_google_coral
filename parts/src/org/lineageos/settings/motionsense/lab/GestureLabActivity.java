package org.lineageos.settings.motionsense.lab;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import org.lineageos.settings.R;

/** Acquires radar subscriptions and an expiring media-routing lease only in the foreground. */
abstract class GestureLabActivity extends LabActivity implements LabGestureClient.Callback {
    private LabGestureClient client;
    private boolean listening;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        client = new LabGestureClient(this, this);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    @Override protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        main.post(heartbeat);
    }
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            if (available(GestureLabActivity.this)) {
                command("lease",0,100);
                if (!listening) { listening = true; client.start(); }
            } else {
                if (listening) { listening = false; client.stop(); }
                command("end",0,100);
                status.setText(R.string.motion_lab_unavailable);
            }
            main.postDelayed(this,2000);
        }
    };
    @Override public void connection(boolean connected) {
        if (connected && resumed) command("lease",0,100);
        if (resumed) status.setText(!available(this) ? R.string.motion_lab_unavailable
                : connected ? R.string.motion_lab_ready : R.string.motion_lab_waiting);
    }
    @Override public final void gesture(int kind, int side, float confidence) {
        if (!resumed || !available(this)) return;
        onGesture(kind,side,Math.max(0,Math.min(1,confidence)));
    }
    abstract void onGesture(int kind, int side, float confidence);
    String gestureName(int kind, int side) {
        return getString(kind == LabGestureClient.TAP ? R.string.motion_lab_tap
                : kind == LabGestureClient.REACH ? R.string.motion_lab_reach
                : side < 0 ? R.string.motion_lab_left : R.string.motion_lab_right);
    }
    @Override protected void onPause() {
        listening = false; client.stop(); super.onPause();
    }
}
