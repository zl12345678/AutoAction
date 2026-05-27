package com.auto.opencv.process;

import com.auto.opencv.utils.ImageProcessor;
import com.auto.vision.OpenCvLoader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test using a real minimap crop where the golden player arrow failed detection.
 */
public class UserMinimapArrowTest {
    @BeforeClass
    public static void loadOpenCv() {
        OpenCvLoader.load();
    }

    @Test
    public void detectsGoldenArrowOnUserMinimap() {
        Mat miniMap = ImageProcessor.loadResourceImage("img/user_minimap_arrow.png");
        Mat template = extractArrowTemplateFromMask(miniMap);

        ArrowMatchResult result = new ArrowMatcher().matchWithConfidence(
                miniMap,
                template,
                10,
                35,
                80,
                80
        );

        assertTrue("arrow should be found: " + result, result.found());
        assertNotNull(result.center());
        assertTrue("confidence should be positive", result.confidence() > 0.0);
    }

    private static Mat extractArrowTemplateFromMask(Mat miniMap) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(miniMap, hsv, Imgproc.COLOR_BGR2HSV);
        Mat mask = new Mat();
        Core.inRange(hsv, new Scalar(10, 80, 80), new Scalar(35, 255, 255), mask);
        org.opencv.core.Rect bounds = Imgproc.boundingRect(mask);
        bounds = shrinkRect(bounds, miniMap);
        return miniMap.submat(bounds).clone();
    }

    private static org.opencv.core.Rect shrinkRect(org.opencv.core.Rect bounds, Mat image) {
        int pad = 2;
        int x = Math.max(0, bounds.x - pad);
        int y = Math.max(0, bounds.y - pad);
        int w = Math.min(image.cols() - x, bounds.width + pad * 2);
        int h = Math.min(image.rows() - y, bounds.height + pad * 2);
        return new org.opencv.core.Rect(x, y, w, h);
    }
}
