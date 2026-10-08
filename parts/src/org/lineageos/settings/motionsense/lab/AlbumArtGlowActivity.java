package org.lineageos.settings.motionsense.lab;

import android.database.ContentObserver;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.CheckBox;
import org.lineageos.settings.R;

/** Opt-in artwork tint, using the same Secure value as the Glow light settings page. */
public final class AlbumArtGlowActivity extends LabActivity {
    private static final String KEY = "aware_album_art_glow";
    private CheckBox enabled;
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { update(); }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); title(R.string.motion_lab_album,R.string.motion_lab_album_intro);
        enabled=new CheckBox(this); enabled.setText(R.string.motion_lab_album_toggle);
        enabled.setTextColor(foreground); enabled.setMinHeight(dp(48));
        enabled.setOnClickListener(view -> {
            try {
                if (!Settings.Secure.putInt(getContentResolver(),KEY,enabled.isChecked()?1:0)) {
                    update(); status.setText(R.string.motion_lab_save_failed); return;
                }
                update();
            } catch (RuntimeException e) { update(); status.setText(R.string.motion_lab_save_failed); }
        });
        content.addView(enabled); content.addView(status);
        content.addView(text(getString(R.string.motion_lab_album_help),14));
    }
    private void update() {
        boolean on=Settings.Secure.getInt(getContentResolver(),KEY,0)==1;
        boolean glow=Settings.Secure.getInt(getContentResolver(),"aware_glow_show",1)==1;
        enabled.setChecked(on); enabled.setEnabled(available(this) && glow);
        status.setText(!available(this) ? R.string.motion_lab_unavailable
                : !glow ? R.string.motion_lab_album_hidden : !on ? R.string.motion_lab_album_off
                : Settings.Secure.getInt(getContentResolver(),"aware_air_dj",0)==1
                ? R.string.motion_lab_album_air_dj : R.string.motion_lab_album_on);
    }
    @Override protected void onResume() {
        super.onResume();
        for (String key : new String[] {KEY,"aware_enabled","aware_glow_show","aware_air_dj"})
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key),false,observer);
        for (String key : new String[] {"aware_allowed","airplane_mode_on","low_power"})
            getContentResolver().registerContentObserver(Settings.Global.getUriFor(key),false,observer);
        update();
    }
    @Override protected void onPause() {
        getContentResolver().unregisterContentObserver(observer); super.onPause();
    }
}
