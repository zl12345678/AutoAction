package com.auto.vision;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalizationRefinerTest {
    @BeforeClass
    public static void loadOpenCv() {
        OpenCvLoader.load();
    }

    @Test
    public void snapToWalkableMovesOffObstacle() {
        Mat map = new Mat(60, 60, CvType.CV_8UC1, new Scalar(255));
        Imgproc.circle(map, new Point(30, 30), 8, new Scalar(0), -1);

        Point onWall = new Point(30, 10);
        Point snapped = LocalizationRefiner.snapToWalkable(map, onWall, 200, 15);
        assertTrue(snapped.y >= 20);
        assertEquals(0.0, map.get((int) snapped.y, (int) snapped.x)[0], 1.0);
    }
}
