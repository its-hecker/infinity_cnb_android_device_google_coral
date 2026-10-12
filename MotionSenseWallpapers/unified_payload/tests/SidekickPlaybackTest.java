package com.hecker.motionsense.wallpapers;

import static org.junit.Assert.*;

import android.view.Choreographer;
import android.service.wallpaper.WallpaperService;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class SidekickPlaybackTest {
    @Test public void cadenceIsEvenAt60_90_120Hz() {
        for (int hz : new int[] {60, 90, 120}) {
            for (boolean idle : new boolean[] {true, false}) {
                SidekickFramePacer pacer = new SidekickFramePacer();
                pacer.setRefreshRate(hz);
                int divisor = (int) Math.ceil(hz / (idle ? 30d : 60d));
                int draws = 0, previous = -divisor;
                for (int i = 0; i < 600; i++) {
                    long timestamp = Math.round(i * 1_000_000_000d / hz)
                            + (i % 3 - 1) * 100_000L;
                    if (pacer.shouldDraw(timestamp, idle)) {
                        assertEquals("irregular cadence at " + hz + "Hz", divisor, i - previous);
                        previous = i;
                        draws++;
                        assertFalse("one render per timestamp", pacer.shouldDraw(timestamp, idle));
                    }
                }
                assertEquals(600 / divisor, draws);
            }
        }
    }

    @Test public void stallsAndModeChangesDoNotCreateCatchupBursts() {
        SidekickFramePacer pacer = new SidekickFramePacer();
        pacer.setRefreshRate(90f);
        assertTrue(pacer.shouldDraw(0, true));
        assertFalse(pacer.shouldDraw(0, false));
        assertTrue(pacer.shouldDraw(100_000_000L, false));
        assertFalse(pacer.shouldDraw(100_000_000L, false));
        assertFalse(pacer.shouldDraw(111_111_111L, false));
        assertTrue(pacer.shouldDraw(122_222_222L, false));
        assertTrue(pacer.shouldDraw(1_000_000_000L, false));
        assertFalse(pacer.shouldDraw(1_011_111_111L, false));
        pacer.reset();
        assertTrue(pacer.shouldDraw(1_011_111_111L, false));
        pacer.setRefreshRate(Float.NaN);
        assertTrue(pacer.shouldDraw(0, true));
        assertFalse(pacer.shouldDraw(16_666_667L, true));
        assertTrue(pacer.shouldDraw(33_333_333L, true));
    }

    @Test public void visibilityAndSurfaceRecreationKeepExactlyOneCallback() throws Exception {
        UnifiedSidekickWallpaper service = Robolectric.buildService(UnifiedSidekickWallpaper.class)
                .create().get();
        WallpaperService.Engine engine = service.onCreateEngine();
        engine.onSurfaceCreated(engine.getSurfaceHolder());
        engine.onVisibilityChanged(true);
        assertEquals(1, callbacksFor(engine));
        engine.onVisibilityChanged(true);
        engine.onVisibilityChanged(true);
        engine.onSurfaceChanged(engine.getSurfaceHolder(), 0, 728, 1536);
        assertEquals("duplicate notifications must not multiply loops", 1, callbacksFor(engine));
        engine.onVisibilityChanged(false);
        assertEquals(0, callbacksFor(engine));
        engine.onVisibilityChanged(true);
        assertEquals(1, callbacksFor(engine));
        engine.onSurfaceDestroyed(engine.getSurfaceHolder());
        assertEquals(0, callbacksFor(engine));
        engine.onSurfaceCreated(engine.getSurfaceHolder());
        assertEquals("surface restoration restarts a still-visible engine", 1, callbacksFor(engine));
        engine.onDestroy();
        assertEquals(0, callbacksFor(engine));
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    // Count actual Android Choreographer records instead of only testing a local boolean.
    private static int callbacksFor(WallpaperService.Engine engine) throws Exception {
        Object[] queues = (Object[]) field(Choreographer.getInstance(), "mCallbackQueues");
        Object record = field(queues[1], "mHead");
        int count = 0;
        while (record != null) {
            if (field(record, "action") == engine) count++;
            record = field(record, "next");
        }
        return count;
    }
}
