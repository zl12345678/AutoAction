package com.auto.vision;

import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PathfindingMapPostProcessorTest {
    @Test
    public void sealExteriorMarksOpenMarginsAsObstacle() {
        OpenCvLoader.load();
        Mat map = new Mat(60, 60, CvType.CV_8UC1);
        map.setTo(new org.opencv.core.Scalar(0));

        PathfindingMapPostProcessor.sealExteriorWalkable(map, 200);

        assertEquals(255, (int) map.get(0, 0)[0]);
        assertEquals(255, (int) map.get(59, 59)[0]);
    }

    @Test
    public void sealExteriorPreservesEnclosedInterior() {
        OpenCvLoader.load();
        Mat map = new Mat(80, 80, CvType.CV_8UC1);
        map.setTo(new org.opencv.core.Scalar(0));
        Imgproc.rectangle(map, new org.opencv.core.Point(10, 10), new org.opencv.core.Point(69, 69), new org.opencv.core.Scalar(255), 3);

        PathfindingMapPostProcessor.sealExteriorWalkable(map, 200);

        assertEquals(0, (int) map.get(40, 40)[0]);
        assertEquals(255, (int) map.get(0, 40)[0]);
    }

    @Test
    public void closeWallGapsConnectsBrokenWallSegments() {
        OpenCvLoader.load();
        Mat map = new Mat(40, 40, CvType.CV_8UC1);
        map.setTo(new org.opencv.core.Scalar(0));
        for (int x = 5; x < 35; x++) {
            if (x < 18 || x > 22) {
                map.put(5, x, 255);
            }
        }

        PathfindingMapPostProcessor.closeWallGaps(map, 7);

        assertTrue(map.get(5, 20)[0] > 200);
    }
}
