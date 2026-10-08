package org.lineageos.settings.motionsense.lab;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.lineageos.settings.R;

/** Two short games driven by detected Soli swipes and taps. */
public final class ArcadeActivity extends GestureLabActivity {
    private final LabEngine engine = new LabEngine(System.nanoTime());
    private Arena arena;
    private TextView score;
    private long frameAt;
    private int displayedScore = -1, displayedSeconds = -1;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        title(R.string.motion_lab_arcade,R.string.motion_lab_arcade_intro);
        content.addView(status);
        addButton(R.string.motion_lab_dodge,() -> start(LabEngine.DODGE));
        addButton(R.string.motion_lab_targets,() -> start(LabEngine.TARGETS));
        score = text("",18); content.addView(score);
        arena = new Arena(); content.addView(arena,new LinearLayout.LayoutParams(-1,dp(320)));
        addButton(R.string.motion_lab_restart,() -> start(engine.game));
        content.addView(text(getString(R.string.motion_lab_games_help),14));
        engine.game = LabEngine.DODGE; updateScore();
    }
    private void start(int game) {
        if (!available(this)) { status.setText(R.string.motion_lab_unavailable); return; }
        engine.start(game); frameAt = SystemClock.elapsedRealtime();
        status.setText(game == LabEngine.DODGE ? R.string.motion_lab_dodge_help : R.string.motion_lab_targets_help);
        updateScore(); arena.invalidate();
    }
    @Override void onGesture(int kind, int side, float confidence) {
        if (kind == LabGestureClient.SWIPE) engine.swipe(side);
        else if (kind == LabGestureClient.TAP) engine.tap();
        if (kind != LabGestureClient.REACH) {
            status.setText(getString(R.string.motion_lab_detected,gestureName(kind,side)));
            updateScore(); arena.invalidate();
        }
    }
    private void updateScore() {
        int seconds = (int)Math.ceil(engine.timeLeft);
        if (engine.score != displayedScore || seconds != displayedSeconds) {
            displayedScore = engine.score; displayedSeconds = seconds;
            score.setText(getString(R.string.motion_lab_score,engine.score,seconds));
        }
    }
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            long now = SystemClock.elapsedRealtime();
            boolean running = engine.running;
            if (available(ArcadeActivity.this)) engine.tick((now-frameAt)/1000f);
            frameAt = now;
            if (running && !engine.running) {
                status.setText(getString(engine.complete ? R.string.motion_lab_finished : R.string.motion_lab_game_over,engine.score));
                arena.announceForAccessibility(status.getText());
            }
            updateScore(); arena.invalidate(); main.postDelayed(this,33);
        }
    };
    @Override protected void onResume() {
        super.onResume(); frameAt = SystemClock.elapsedRealtime(); main.post(frame);
    }
    private final class Arena extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Arena() { super(ArcadeActivity.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription(getString(R.string.motion_lab_games_help)); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w=getWidth(), h=getHeight();
            paint.setColor(surface); canvas.drawRoundRect(0,0,w,h,dp(20),dp(20),paint);
            if (engine.game == LabEngine.TARGETS) {
                paint.setColor(accent); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(80));
                canvas.drawText(engine.targetSide < 0 ? "\u2190" : "\u2192",w/2,h*.58f,paint);
                paint.setColor(foreground); paint.setTextSize(dp(16));
                canvas.drawText(getString(engine.targetSide < 0 ? R.string.motion_lab_left : R.string.motion_lab_right),w/2,h*.8f,paint);
            } else {
                paint.setColor(background);
                for (int lane=1; lane<3; lane++) canvas.drawRect(w*lane/3-dp(1),dp(14),w*lane/3+dp(1),h-dp(14),paint);
                paint.setColor(Color.rgb(255,116,110));
                float x=w*(engine.obstacleLane+.5f)/3, y=h*engine.obstacleY;
                if (engine.running) canvas.drawRoundRect(x-dp(17),y-dp(17),x+dp(17),y+dp(17),dp(6),dp(6),paint);
                x=w*(engine.lane+.5f)/3; y=h*.91f;
                paint.setColor(engine.shield > 0 ? Color.rgb(75,230,170) : accent);
                canvas.drawCircle(x,y,dp(engine.shield > 0 ? 23 : 17),paint);
            }
            if (!engine.running) {
                paint.setColor(foreground); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(17));
                canvas.drawText(getString(R.string.motion_lab_air_tap_start),w/2,h*.28f,paint);
            }
        }
    }
}
