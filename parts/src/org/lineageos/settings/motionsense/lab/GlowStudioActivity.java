package org.lineageos.settings.motionsense.lab;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import org.lineageos.settings.R;

/** Draft glow settings with an eight-second, unsaved preview on Oslo's existing surface. */
public final class GlowStudioActivity extends LabActivity {
    private int style, speed;
    private CheckBox trails;
    private TextView speedLabel;
    private StudioView studio;
    private Button preview;
    private long previewUntil;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); title(R.string.motion_lab_studio,R.string.motion_lab_studio_intro);
        style=Math.max(0,Math.min(4,Settings.Secure.getInt(getContentResolver(),"aware_glow_style",0)));
        speed=Math.max(50,Math.min(200,Settings.Secure.getInt(getContentResolver(),"aware_glow_speed",100)));
        boolean savedTrails=Settings.Secure.getInt(getContentResolver(),"aware_gesture_trails",0)==1;
        if(state!=null) { style=state.getInt("style",style); speed=state.getInt("speed",speed); savedTrails=state.getBoolean("trails",savedTrails); }
        RadioGroup styles=new RadioGroup(this);
        String[] names=getResources().getStringArray(R.array.motion_lab_styles);
        for(int i=0;i<names.length;i++) {
            RadioButton choice=new RadioButton(this); choice.setId(100+i); choice.setText(names[i]);
            choice.setTextColor(foreground); choice.setMinHeight(dp(48)); styles.addView(choice);
        }
        styles.check(100+style); content.addView(styles);
        styles.setOnCheckedChangeListener((group,id) -> { style=id-100; if(studio!=null) studio.invalidate(); });
        speedLabel=text("",16); content.addView(speedLabel); updateSpeed();
        SeekBar tempo=new SeekBar(this); tempo.setMin(50); tempo.setMax(200); tempo.setProgress(speed);
        tempo.setContentDescription(getString(R.string.motion_lab_speed_label));
        tempo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b,int value,boolean fromUser) { speed=value; updateSpeed(); }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        content.addView(tempo,new LinearLayout.LayoutParams(-1,dp(48)));
        trails=new CheckBox(this); trails.setText(R.string.motion_lab_trails); trails.setTextColor(foreground);
        trails.setMinHeight(dp(48)); trails.setChecked(savedTrails); content.addView(trails);
        content.addView(text(getString(R.string.motion_lab_trails_intro),14));
        studio=new StudioView(); content.addView(studio,new LinearLayout.LayoutParams(-1,dp(140)));
        content.addView(status); status.setText(R.string.motion_lab_draft);
        preview=button(getString(R.string.motion_lab_preview),this::preview);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56)); lp.topMargin=dp(10);
        content.addView(preview,lp);
        addButton(R.string.motion_lab_stop_preview,this::stopPreview);
        addButton(R.string.motion_lab_apply,this::apply);
    }
    private void updateSpeed() { speedLabel.setText(getString(R.string.motion_lab_speed,speed)); }
    private void preview() {
        if(!available(this)) { status.setText(R.string.motion_lab_unavailable); return; }
        previewUntil=SystemClock.elapsedRealtime()+8000;
        command("preview",style,speed,trails.isChecked()); status.setText(R.string.motion_lab_preview_active);
    }
    private void stopPreview() {
        command("end",0,100); previewUntil=0; status.setText(R.string.motion_lab_draft);
    }
    private void apply() {
        command("end",0,100); previewUntil=0;
        try {
            boolean written=Settings.Secure.putInt(getContentResolver(),"aware_glow_style",style)
                    && Settings.Secure.putInt(getContentResolver(),"aware_glow_speed",speed)
                    && Settings.Secure.putInt(getContentResolver(),"aware_gesture_trails",trails.isChecked()?1:0);
            status.setText(written ? R.string.motion_lab_saved : R.string.motion_lab_save_failed);
        } catch(RuntimeException e) { status.setText(R.string.motion_lab_save_failed); }
    }
    private final Runnable frame=new Runnable() {
        @Override public void run() {
            if(!resumed) return;
            boolean allowed=available(GlowStudioActivity.this); preview.setEnabled(allowed);
            if(previewUntil>0 && (!allowed || SystemClock.elapsedRealtime()>=previewUntil)) {
                command("end",0,100); previewUntil=0; status.setText(R.string.motion_lab_preview_ended);
            }
            studio.invalidate(); main.postDelayed(this,33);
        }
    };
    @Override protected void onResume() {
        super.onResume(); if(!available(this)) status.setText(R.string.motion_lab_unavailable); main.post(frame);
    }
    @Override protected void onPause() { previewUntil=0; status.setText(R.string.motion_lab_draft); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("style",style); state.putInt("speed",speed);
        state.putBoolean("trails",trails.isChecked());
    }
    private final class StudioView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        StudioView() { super(GlowStudioActivity.this); setContentDescription(getString(R.string.motion_lab_draft)); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float w=getWidth(),h=getHeight();
            float t=(SystemClock.elapsedRealtime()%2400)/2400f*speed/100f;
            paint.setShader(null); paint.setColor(surface); c.drawRoundRect(0,0,w,h,dp(20),dp(20),paint);
            paint.setColor(style==1 ? 0xffff59d5 : accent);
            paint.setAlpha(style==4 ? 110 : 255); paint.setStrokeWidth(dp(style==4 ? 3 : 6));
            if(style==2) paint.setShader(new LinearGradient(0,0,w,0,new int[] {0xff32e6a8,0xffad66ff,0xff32e6a8},null,Shader.TileMode.MIRROR));
            if(style==3) for(int i=0;i<22;i++) {
                float x=w*.1f+w*.8f*i/22; c.drawRect(x,h*.48f,x+w*.8f/32,h*.52f,paint);
            } else c.drawLine(w*.1f,h*.5f,w*.9f,h*.5f,paint);
            if(trails.isChecked()) { paint.setShader(null); paint.setColor(accent);
                for(int i=0;i<9;i++) { paint.setAlpha(255-i*25); float x=w*(.12f+.76f*((t-i*.015f+2)%1));
                    c.drawCircle(x,h*.5f,dp(6-i*.45f),paint); }
            }
            paint.setShader(null); paint.setAlpha(255); paint.setColor(foreground);
            paint.setTextSize(dp(13)); paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(getString(R.string.motion_lab_local_preview),w/2,h*.84f,paint);
        }
    }
}
