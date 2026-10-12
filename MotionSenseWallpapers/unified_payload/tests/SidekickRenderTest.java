package com.hecker.motionsense.wallpapers;

import static org.junit.Assert.*;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SidekickRenderTest {
    private static final String[][] CHARACTERS = {
        {"hecker", "Hecker"}, {"mochi", "Mochi"}, {"biscuit", "Biscuit"},
        {"whisk_crumb", "WhiskCrumb"}, {"pip", "Pip"}, {"drift", "Drift"},
        {"orbit", "Orbit"}, {"aurora", "Aurora"}, {"doraemon", "Doraemon"},
        {"ben10", "Ben10"}, {"batman", "Batman"}, {"tom_jerry", "TomJerry"},
        {"nova", "Nova"}, {"kumo", "Kumo"}, {"ember", "Ember"}, {"byte", "Byte"},
    };

    @Test public void pickerComponentsSelectTheirOwnCharacter() throws Exception {
        for (String[] spec : CHARACTERS) {
            Class<?> type = Class.forName(UnifiedSidekickWallpaper.class.getName() + "$" + spec[1]);
            UnifiedSidekickWallpaper wallpaper = (UnifiedSidekickWallpaper) type.getDeclaredConstructor().newInstance();
            assertEquals("wallpaper_" + spec[0] + ".json", wallpaper.initialCharacterAsset());
        }
        assertNull(new UnifiedSidekickWallpaper().initialCharacterAsset());
    }

    @Test public void allCharactersRenderAcrossMarkersDensitiesAndPreviewSizes() throws Exception {
        File output = new File(System.getProperty("sidekick.render.output"));
        File thumbnails = new File(output, "thumbnails");
        assertTrue(thumbnails.mkdirs() || thumbnails.isDirectory());
        StringBuilder report = new StringBuilder();
        int frames = 0;
        float originalDensity = Resources.getSystem().getDisplayMetrics().density;
        try {
            for (float density : new float[] {1f, 3.5f}) {
                Resources.getSystem().getDisplayMetrics().density = density;
                for (String[] spec : CHARACTERS) {
                    LottieComposition composition;
                    try (InputStream in = RuntimeEnvironment.getApplication().getAssets().open(
                            "sidekick/wallpaper_" + spec[0] + ".json")) {
                        composition = LottieCompositionFactory.fromJsonInputStreamSync(in, null).getValue();
                    }
                    assertNotNull(spec[0] + " composition failed to load", composition);
                    LottieDrawable drawable = new LottieDrawable();
                    drawable.setComposition(composition);
                    SidekickRenderer renderer = new SidekickRenderer();
                    for (int[] size : new int[][] {{432, 936}, {728, 1536}}) {
                        Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
                        bitmap.setDensity(Bitmap.DENSITY_NONE);
                        Canvas canvas = new Canvas(bitmap);
                        for (int frame : new int[] {60, 360, 720}) {
                            bitmap.eraseColor(Color.BLACK);
                            drawable.setFrame(frame);
                            renderer.draw(canvas, drawable, size[0], size[1], 1.04f,
                                    frame == 360 ? 1.025f : 1f, frame == 360 ? 2f : 0f, 0f, 0f);
                            String metrics = verifyPicture(bitmap, spec[0], frame);
                            report.append(String.format(Locale.ROOT, "%s frame=%d density=%.1f size=%dx%d %s\n",
                                    spec[0], frame, density, size[0], size[1], metrics));
                            frames++;
                            if (density == 1f && size[0] == 432 && frame == 60) {
                                save(bitmap, new File(output, "sidekick_" + spec[0] + ".png"));
                                int crop = bitmap.getWidth();
                                int top = Math.round(bitmap.getHeight() * .55f - crop * .5f);
                                Bitmap thumbnail = Bitmap.createBitmap(bitmap, 0, top, crop, crop);
                                save(thumbnail, new File(thumbnails, "sidekick_" + spec[0] + "_thumbnail.png"));
                                thumbnail.recycle();
                            }
                        }
                        bitmap.recycle();
                    }
                    renderer.clear();
                }
            }
        } finally {
            Resources.getSystem().getDisplayMetrics().density = originalDensity;
        }
        report.append("Rendered frames: ").append(frames).append("\nNative Android bitmap graphics: PASS\n");
        Files.write(new File(output, "render-validation.txt").toPath(),
                    report.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String verifyPicture(Bitmap bitmap, String key, int frame) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        boolean[] columns = new boolean[w];
        Set<Integer> colors = new HashSet<>();
        int foreground = 0;
        for (int y = Math.round(h * .25f); y < Math.round(h * .88f); y++) {
            for (int x = Math.round(w * .1f); x < Math.round(w * .9f); x++) {
                int color = pixels[y * w + x];
                int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
                if (Color.alpha(color) > 200 && Math.max(r, Math.max(g, b)) > 55) {
                    foreground++;
                    columns[x] = true;
                    colors.add((r / 16 << 8) | (g / 16 << 4) | b / 16);
                }
            }
        }
        int visibleColumns = 0;
        for (boolean column : columns) if (column) visibleColumns++;
        String name = key + " frame " + frame;
        assertTrue(name + " collapsed horizontally: " + visibleColumns, visibleColumns > w * .22f);
        assertTrue(name + " blank or only a sliver: " + foreground, foreground > w * h * .025f);
        assertTrue(name + " lost character artwork: colors=" + colors.size(), colors.size() >= 16);
        return "columns=" + visibleColumns + " foreground=" + foreground + " colors=" + colors.size() + " PASS";
    }

    private static void save(Bitmap bitmap, File file) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
    }
}
