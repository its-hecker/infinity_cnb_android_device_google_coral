package org.lineageos.settings.motionsense.lab;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.UUID;
import java.util.ArrayList;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import org.lineageos.settings.R;

/** Native, theme-aware pages with automatic cleanup of transient experiment sessions. */
abstract class LabActivity extends Activity {
    final Handler main = new Handler(Looper.getMainLooper());
    final String token = UUID.randomUUID().toString();
    LinearLayout content;
    TextView status;
    int foreground, background, surface, accent = Color.rgb(58, 174, 255);
    boolean resumed;
    private final ArrayList<LottieDrawable> illustrations = new ArrayList<>();
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
        content.addView(heading);
        int animation = resource == R.string.motion_lab_arcade ? R.raw.motion_lab_arcade
                : resource == R.string.motion_lab_training ? R.raw.motion_lab_training
                : resource == R.string.motion_lab_panel ? R.raw.motion_lab_panel
                : resource == R.string.motion_lab_studio ? R.raw.motion_lab_studio
                : resource == R.string.motion_lab_album ? R.raw.motion_lab_album : 0;
        if (animation != 0) illustration(animation, description, 180);
        content.addView(text(getString(description),15));
    }
    /** Use the native ImageView with LottieDrawable so lab pages need no AppCompat theme. */
    void illustration(int resource, int description, int height) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setContentDescription(getString(description));
        LottieDrawable drawable = new LottieDrawable();
        drawable.setRepeatCount(ValueAnimator.INFINITE);
        drawable.setIgnoreDisabledSystemAnimations(false);
        view.setImageDrawable(drawable); illustrations.add(drawable);
        content.addView(view, new LinearLayout.LayoutParams(-1,dp(height)));
        LottieCompositionFactory.fromRawRes(this,resource).addListener(composition -> {
            if (isDestroyed()) return;
            drawable.setComposition(composition);
            tintIllustration(drawable);
            if (resumed && ValueAnimator.areAnimatorsEnabled()) drawable.playAnimation();
            else drawable.setProgress(.25f);
        }).addFailureListener(error -> { if (!isDestroyed()) view.setVisibility(View.GONE); });
    }
    private void tint(LottieDrawable drawable, String key, int color) {
        drawable.addValueCallback(new KeyPath("**",key,"**"),LottieProperty.COLOR_FILTER,
                frame -> new PorterDuffColorFilter(color,PorterDuff.Mode.SRC_ATOP));
    }
    private void tintIllustration(LottieDrawable drawable) {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int primary = getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        int secondary = getColor(dark ? android.R.color.system_accent2_200 : android.R.color.system_accent2_600);
        int tertiary = getColor(dark ? android.R.color.system_accent3_200 : android.R.color.system_accent3_600);
        tint(drawable,".illoBg2",surface); tint(drawable,".illoBg3",background);
        tint(drawable,".illoFixedWhite",Color.WHITE); tint(drawable,".illoFixedBlack",Color.BLACK);
        for (String key : new String[] {".illoCoreTheme0",".illoCoreTheme2",".illoCoreTheme3",
                ".illoAccentDynamic1",".illoAccentDynamic2"}) tint(drawable,key,primary);
        for (String key : new String[] {".illoCoreSecondary1",".illoCoreSecondary2"}) tint(drawable,key,secondary);
        for (String key : new String[] {".illoCoreTertiary1",".illoCoreTertiary2"}) tint(drawable,key,tertiary);
        boolean custom=Settings.Secure.getInt(getContentResolver(),"aware_glow_custom",1)!=0;
        int hue=custom ? Settings.Secure.getInt(getContentResolver(),"aware_glow_hue",270) : 215;
        tint(drawable,".motionGlow",Color.HSVToColor(new float[] {hue,custom?.55f:.5f,1f}));
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
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        for (LottieDrawable drawable : illustrations) {
            tintIllustration(drawable);
            if (drawable.getComposition() != null && ValueAnimator.areAnimatorsEnabled()) drawable.resumeAnimation();
            else drawable.setProgress(.25f);
        }
    }
    @Override protected void onPause() {
        resumed = false; main.removeCallbacksAndMessages(null); command("end",0,100);
        for (LottieDrawable drawable : illustrations) drawable.pauseAnimation();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); super.onPause();
    }
    @Override protected void onDestroy() {
        for (LottieDrawable drawable : illustrations) drawable.cancelAnimation();
        illustrations.clear(); super.onDestroy();
    }
}
