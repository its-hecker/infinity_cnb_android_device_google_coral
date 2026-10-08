package org.lineageos.settings.motionsense.lab;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.HashMap;
import org.lineageos.settings.R;

/** Visible foreground control deck; no hidden overlay or background gesture actions. */
public final class ControlPanelActivity extends GestureLabActivity {
    private final int[] labels={R.string.motion_lab_play_pause,R.string.motion_lab_volume_up,
            R.string.motion_lab_volume_down,R.string.motion_lab_previous,R.string.motion_lab_next,R.string.motion_lab_torch};
    private final Button[] buttons=new Button[labels.length];
    private int selected;
    private long lastAction=-1;
    private String torchId;
    private final HashMap<String,Boolean> torchStates=new HashMap<>();
    private CameraManager cameras;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); title(R.string.motion_lab_panel,R.string.motion_lab_panel_intro);
        content.addView(status);
        for (int i=0;i<labels.length;i++) {
            final int index=i;
            buttons[i]=button(getString(labels[i]),() -> { selected=index; updateSelection(); execute(); });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(60)); lp.topMargin=dp(10);
            content.addView(buttons[i],lp);
        }
        content.addView(text(getString(R.string.motion_lab_panel_help),14));
        cameras=getSystemService(CameraManager.class); updateSelection();
    }
    private void updateSelection() {
        for(int i=0;i<buttons.length;i++) {
            buttons[i].setText((i==selected ? "\u25b6  " : "")+getString(labels[i]));
            buttons[i].setSelected(i==selected);
            buttons[i].setContentDescription(getString(i==selected ? R.string.motion_lab_selected : R.string.motion_lab_control,getString(labels[i])));
        }
    }
    @Override void onGesture(int kind,int side,float confidence) {
        if(kind==LabGestureClient.SWIPE) { selected=LabEngine.cycle(selected,side,labels.length); updateSelection(); status.setText(getString(R.string.motion_lab_selected,getString(labels[selected]))); }
        else if(kind==LabGestureClient.TAP) execute();
    }
    private void execute() {
        long now=SystemClock.elapsedRealtime();
        if(lastAction>=0 && now-lastAction<500) return;
        lastAction=now;
        try {
            if(selected==5) { toggleTorch(); return; }
            MediaSessionManager sessions=getSystemService(MediaSessionManager.class);
            MediaController target=null;
            if(sessions!=null) for(MediaController session:sessions.getActiveSessions(null)) {
                PlaybackState state=session.getPlaybackState();
                if(state==null) continue;
                if(state.getState()==PlaybackState.STATE_PLAYING) { target=session; break; }
                if(selected==0 && target==null && state.getState()==PlaybackState.STATE_PAUSED) target=session;
            }
            if(target==null) { status.setText(R.string.motion_lab_no_media); return; }
            PlaybackState state=target.getPlaybackState();
            if(state==null) { status.setText(R.string.motion_lab_no_media); return; }
            long required=selected==0 ? (state.getState()==PlaybackState.STATE_PLAYING ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY)
                    : selected==3 ? PlaybackState.ACTION_SKIP_TO_PREVIOUS : PlaybackState.ACTION_SKIP_TO_NEXT;
            if(selected==1 || selected==2) {
                MediaController.PlaybackInfo info=target.getPlaybackInfo();
                if(info==null || info.getVolumeControl()==0) { status.setText(R.string.motion_lab_unsupported); return; }
                target.adjustVolume(selected==1 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,0);
            } else if((state.getActions() & (required | (selected==0 ? PlaybackState.ACTION_PLAY_PAUSE : 0)))==0) {
                status.setText(R.string.motion_lab_unsupported); return;
            } else if(selected==0) {
                if(state.getState()==PlaybackState.STATE_PLAYING) target.getTransportControls().pause();
                else target.getTransportControls().play();
            } else if(selected==3) target.getTransportControls().skipToPrevious();
            else target.getTransportControls().skipToNext();
            status.setText(getString(R.string.motion_lab_executed,getString(labels[selected])));
        } catch(RuntimeException e) { status.setText(R.string.motion_lab_action_failed); }
    }
    private void toggleTorch() {
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA},10); return;
        }
        try {
            if(cameras==null) { status.setText(R.string.motion_lab_no_torch); return; }
            if(torchId==null) for(String id:cameras.getCameraIdList()) {
                CameraCharacteristics c=cameras.getCameraCharacteristics(id);
                if(Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) { torchId=id; break; }
            }
            if(torchId==null) { status.setText(R.string.motion_lab_no_torch); return; }
            cameras.setTorchMode(torchId,!Boolean.TRUE.equals(torchStates.get(torchId)));
        } catch(CameraAccessException | RuntimeException e) { status.setText(R.string.motion_lab_action_failed); }
    }
    private final CameraManager.TorchCallback torchCallback=new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String id,boolean enabled) {
            torchStates.put(id,enabled);
            if(id.equals(torchId) && resumed) status.setText(enabled ? R.string.motion_lab_torch_on : R.string.motion_lab_torch_off);
        }
    };
    @Override protected void onResume() {
        super.onResume();
        if(cameras!=null) try { cameras.registerTorchCallback(torchCallback,main); } catch(RuntimeException ignored) {}
    }
    @Override protected void onPause() {
        if(cameras!=null) try { cameras.unregisterTorchCallback(torchCallback); } catch(RuntimeException ignored) {}
        // A torch selected by the user keeps its state; only gesture subscriptions stop.
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==10) status.setText(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED
                ? R.string.motion_lab_torch_permission_ready : R.string.motion_lab_torch_permission_denied);
    }
}
