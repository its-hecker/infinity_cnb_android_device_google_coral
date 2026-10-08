package org.lineageos.settings.motionsense.lab;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.lineageos.settings.R;

/** Guided reach, left, right and air-tap practice with real detector feedback. */
public final class TrainingActivity extends GestureLabActivity {
    private final LabEngine engine = new LabEngine(0);
    private TextView instruction;
    private PracticeView practice;
    private long feedbackAt;
    private boolean matched;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        title(R.string.motion_lab_training,R.string.motion_lab_training_intro);
        content.addView(status); instruction=text("",21); content.addView(instruction);
        practice=new PracticeView(); content.addView(practice,new LinearLayout.LayoutParams(-1,dp(260)));
        addButton(R.string.motion_lab_practice_again,() -> { engine.start(LabEngine.TRAINING); updateInstruction(); practice.invalidate(); });
        content.addView(text(getString(R.string.motion_lab_training_help),14));
        engine.start(LabEngine.TRAINING); updateInstruction();
    }
    private void updateInstruction() {
        int[] prompts={R.string.motion_lab_lesson_reach,R.string.motion_lab_lesson_left,
                R.string.motion_lab_lesson_right,R.string.motion_lab_lesson_tap,R.string.motion_lab_lesson_done};
        instruction.setText(prompts[engine.lesson]);
        practice.setContentDescription(instruction.getText());
    }
    @Override void onGesture(int kind,int side,float confidence) {
        if (engine.complete) return;
        int before=engine.lesson;
        if (kind==LabGestureClient.SWIPE) engine.swipe(side);
        else if (kind==LabGestureClient.TAP) engine.tap();
        else engine.reach();
        matched=engine.lesson>before; feedbackAt=SystemClock.elapsedRealtime();
        String name=gestureName(kind,side);
        status.setText(confidence > 0 ? getString(R.string.motion_lab_training_feedback,
                name,Math.round(confidence*100),getString(matched ? R.string.motion_lab_match : R.string.motion_lab_try_prompt))
                : getString(R.string.motion_lab_training_feedback_no_confidence,name,
                getString(matched ? R.string.motion_lab_match : R.string.motion_lab_try_prompt)));
        updateInstruction(); practice.invalidate();
    }
    private final class PracticeView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PracticeView() { super(TrainingActivity.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float w=getWidth(),h=getHeight();
            paint.setColor(surface); canvas.drawRoundRect(0,0,w,h,dp(20),dp(20),paint);
            long age=SystemClock.elapsedRealtime()-feedbackAt;
            paint.setColor(age<800 ? (matched ? 0xff46dba7 : 0xffffb663) : accent);
            paint.setStrokeWidth(dp(5)); paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(w/2,h/2,dp(60)+(age<800 ? dp(14)*age/800f : 0),paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(54));
            String[] symbols={"\u25ce","\u2190","\u2192","\u25cf","\u2713"};
            canvas.drawText(symbols[engine.lesson],w/2,h/2+dp(18),paint);
            if (age>=0 && age<800 && resumed) postInvalidateDelayed(33);
        }
    }
}
