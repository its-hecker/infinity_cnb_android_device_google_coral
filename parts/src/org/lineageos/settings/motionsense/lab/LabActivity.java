package org.lineageos.settings.motionsense.lab;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.UUID;
import org.lineageos.settings.R;

/** Native, theme-aware pages with automatic cleanup of transient experiment sessions. */
abstract class LabActivity extends Activity {
    final Handler main = new Handler(Looper.getMainLooper());
    final String token = UUID.randomUUID().toString();
    LinearLayout content;
    TextView status;
    int foreground, background, surface, accent = Color.rgb(58, 174, 255);
    boolean resumed;
    final int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        foreground = dark ? Color.rgb(237,243,255) : Color.rgb(21,34,53);
        background = dark ? Color.rgb(10,17,29) : Color.rgb(243,247,255);
        surface = dark ? Color.rgb(23,35,54) : Color.WHITE;
        getWindow().setStatusBarColor(background); getWindow().setNavigationBarColor(background);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(background);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(12),dp(20),dp(24)); scroll.addView(content);
        setContentView(scroll);
        Button back = button(getString(R.string.motion_lab_back), this::finish);
        content.addView(back, new LinearLayout.LayoutParams(-1,dp(48)));
        status = text(getString(R.string.motion_lab_waiting), 14);
    }
    TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextColor(foreground);
        view.setTextSize(size); view.setPadding(0,dp(8),0,dp(8)); return view;
    }
    void title(int resource, int description) {
        TextView heading = text(getString(resource), 28); heading.setTypeface(null,Typeface.BOLD);
        content.addView(heading); content.addView(text(getString(description),15));
    }
    Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(foreground);
        b.setTextSize(15); b.setMinHeight(dp(48));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(surface); shape.setCornerRadius(dp(16));
        shape.setStroke(dp(1), accent); b.setBackground(shape); b.setOnClickListener(v -> action.run());
        b.setPadding(dp(12),dp(8),dp(12),dp(8)); return b;
    }
    void addButton(int text, Runnable action) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(56)); lp.topMargin = dp(10);
        content.addView(button(getString(text),action),lp);
    }
    static boolean available(Activity activity) {
        return Settings.Secure.getInt(activity.getContentResolver(), "aware_enabled", 0) == 1
                && Settings.Global.getInt(activity.getContentResolver(), "aware_allowed", 0) == 1
                && Settings.Global.getInt(activity.getContentResolver(), "airplane_mode_on", 0) == 0
                && Settings.Global.getInt(activity.getContentResolver(), "low_power", 0) == 0;
    }
    void command(String command, int style, int speed) {
        command(command,style,speed,false);
    }
    void command(String command, int style, int speed, boolean trails) {
        for (String target : new String[] {"com.google.oslo", "com.android.systemui"}) {
            Intent intent = new Intent("com.google.oslo.EXPERIMENT_COMMAND").setPackage(target)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("command",command)
                    .putExtra("token",token).putExtra("style",style).putExtra("speed",speed)
                    .putExtra("trails",trails);
            sendBroadcast(intent, "android.permission.WRITE_SECURE_SETTINGS");
        }
    }
    @Override protected void onResume() { super.onResume(); resumed = true; }
    @Override protected void onPause() {
        resumed = false; main.removeCallbacksAndMessages(null); command("end",0,100);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); super.onPause();
    }
}
