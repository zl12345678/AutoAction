package com.auto.opencv.process;

import com.auto.config.MapPreprocessConfig;
import com.auto.vision.OpenCvLoader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MapMatcherLocalizationTest {
    @BeforeClass
    public static void loadOpenCv() {
        OpenCvLoader.load();
    }

    @Test
    public void locateMapsArrowOffsetWithinPatch() {
        Mat largeMap = new Mat(220, 220, CvType.CV_8UC1, new Scalar(255));
        Imgproc.rectangle(largeMap, new Point(70, 70), new Point(150, 150), new Scalar(0), -1);
        Imgproc.rectangle(largeMap, new Point(90, 90), new Point(130, 130), new Scalar(255), -1);
        addOrbFriendlyTexture(largeMap, 70, 70, 80, 80);

        Rect truth = new Rect(70, 70, 60, 60);
        Mat patch = new Mat(largeMap, truth).clone();
        Point arrowInPatch = new Point(42, 28);
        MapMatcher matcher = new MapMatcher(largeMap, patch);
        MapMatchResult result = matcher.locate(arrowInPatch);

        assertTrue("expected ORB/SIFT homography match: " + result.method(), result.found());
        assertNotNull(result.mapPoint());
        assertTrue(result.confidence() > 0.2);
        assertEquals(112.0, result.mapPoint().x, 15.0);
        assertEquals(98.0, result.mapPoint().y, 15.0);
    }

    private static void addOrbFriendlyTexture(Mat mat, int x0, int y0, int x1, int y1) {
        for (int i = 0; i < 12; i++) {
            int x = x0 + 5 + i * 4;
            int y = y0 + 5 + (i % 5) * 6;
            Imgproc.circle(mat, new Point(x, y), 2, new Scalar(180 - i * 8), -1);
        }
    }

    @Test
    public void forLocalizationUsesSharedPreprocess() {
        Mat largeMap = new Mat(180, 180, CvType.CV_8UC3, new Scalar(30, 30, 30));
        Imgproc.rectangle(largeMap, new Point(40, 40), new Point(120, 120), new Scalar(220, 220, 220), -1);
        addOrbFriendlyTexture(largeMap, 40, 40, 80, 80);

        Mat patch = new Mat(50, 50, CvType.CV_8UC3, new Scalar(30, 30, 30));
        Imgproc.rectangle(patch, new Point(5, 5), new Point(45, 45), new Scalar(220, 220, 220), -1);
        addOrbFriendlyTexture(patch, 5, 5, 45, 45);

        MapPreprocessConfig config = new MapPreprocessConfig(
                MapPreprocessConfig.defaults().mapRegion(),
                true,
                127,
                false,
                false,
                10,
                35,
                80,
                80,
                0.30,
                0.05,
                false
        );

        MapMatcher matcher = MapMatcher.forLocalization(largeMap, patch, config);
        MapMatchResult result = matcher.locate(new Point(25, 25));
        assertTrue(result.found());
    }
}
