package org.lineageos.settings.motionsense.lab;

import java.util.Random;

/** Deterministic game and lesson rules, independent of Android UI and radar transport. */
public final class LabEngine {
    public static final int DODGE = 0, TARGETS = 1, TRAINING = 2;
    private final Random random;
    public int game, lane = 1, obstacleLane, targetSide = 1, score, lesson;
    public float obstacleY, timeLeft = 30, shield;
    public boolean running, complete;

    public LabEngine(long seed) { random = new Random(seed); }
    public void start(int type) {
        game = type; lane = 1; score = 0; lesson = 0; complete = false;
        obstacleY = -0.1f; obstacleLane = random.nextInt(3);
        targetSide = random.nextBoolean() ? 1 : -1;
        timeLeft = 30; shield = 0; running = true;
    }
    public void swipe(int side) {
        if (!running || side == 0) return;
        if (game == DODGE) lane = Math.max(0, Math.min(2, lane + (side > 0 ? 1 : -1)));
        else if (game == TARGETS && side == targetSide) {
            score++; targetSide = random.nextBoolean() ? 1 : -1;
        } else if (game == TRAINING && ((lesson == 1 && side < 0) || (lesson == 2 && side > 0))) {
            lesson++;
        }
    }
    public void tap() {
        if (!running) { start(game); return; }
        if (game == DODGE && shield <= 0) shield = .8f;
        if (game == TRAINING && lesson == 3) { lesson = 4; complete = true; running = false; }
    }
    public void reach() { if (running && game == TRAINING && lesson == 0) lesson = 1; }
    public void tick(float delta) {
        if (!running || game == TRAINING) return;
        float dt = Math.max(0, Math.min(.1f, delta));
        timeLeft = Math.max(0, timeLeft - dt); shield = Math.max(0, shield - dt);
        if (timeLeft <= 0) { running = false; complete = true; return; }
        if (game == DODGE) {
            obstacleY += dt * (.35f + Math.min(score, 20) * .012f);
            if (obstacleY >= .9f && obstacleLane == lane && shield <= 0) { running = false; return; }
            if (obstacleY > 1.06f) { score++; obstacleY = -.1f; obstacleLane = random.nextInt(3); }
        }
    }
    public static int physicalSide(int direction) {
        if (direction == 1 || direction == 2 || direction == 8) return 1;
        if (direction == 4 || direction == 5 || direction == 6) return -1;
        return 0;
    }
    public static int cycle(int selected, int side, int count) {
        if (count <= 0) return 0;
        return Math.floorMod(selected + (side > 0 ? 1 : -1), count);
    }
}
