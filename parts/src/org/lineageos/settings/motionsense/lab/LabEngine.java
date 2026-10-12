package org.lineageos.settings.motionsense.lab;

/** Guided gesture lesson rules, independent of Android UI and radar transport. */
public final class LabEngine {
    public int lesson;
    public boolean running, complete;

    public void start() {
        lesson = 0; complete = false; running = true;
    }
    public void swipe(int side) {
        if (running && ((lesson == 1 && side < 0) || (lesson == 2 && side > 0))) {
            lesson++;
        }
    }
    public void tap() {
        if (!running) { start(); return; }
        if (lesson == 3) { lesson = 4; complete = true; running = false; }
    }
    public void reach() { if (running && lesson == 0) lesson = 1; }
    public static int physicalSide(int direction) {
        if (direction == 1 || direction == 2 || direction == 8) return 1;
        if (direction == 4 || direction == 5 || direction == 6) return -1;
        return 0;
    }
}
