package com.auto.vision;

import com.auto.opencv.utils.ImageProcessor;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WallEndpointCounterTest {

    @Test
    public void closedLoopHasZeroEndpoints() {
        OpenCvLoader.load();
        Mat map = new Mat(60, 60, CvType.CV_8UC1, new Scalar(0));
        Imgproc.circle(map, new org.opencv.core.Point(30, 30), 20, new Scalar(255), 1);

        WallEndpointCounter.WallEndpointAnalysis analysis =
                WallEndpointCounter.analyze(map, 200);

        assertTrue(analysis.wallPixels() > 0);
        assertEquals(0, analysis.endpointCount());
        assertTrue(analysis.closed());
    }

    @Test
    public void openLineHasTwoEndpoints() {
        OpenCvLoader.load();
        Mat map = new Mat(40, 40, CvType.CV_8UC1, new Scalar(0));
        Imgproc.line(map, new org.opencv.core.Point(5, 20), new org.opencv.core.Point(34, 20), new Scalar(255), 1);

        WallEndpointCounter.WallEndpointAnalysis analysis =
                WallEndpointCounter.analyze(map, 200);

        assertEquals(2, analysis.endpointCount());
        assertFalse(analysis.closed());
    }

    @Test
    public void brokenRectangleHasAtLeastTwoEndpoints() {
        OpenCvLoader.load();
        Mat map = new Mat(60, 60, CvType.CV_8UC1, new Scalar(0));
        Imgproc.rectangle(map, new org.opencv.core.Point(10, 10), new org.opencv.core.Point(49, 49), new Scalar(255), 1);
        for (int x = 10; x <= 49; x++) {
            map.put(10, x, 0);
        }

        WallEndpointCounter.WallEndpointAnalysis analysis =
                WallEndpointCounter.analyze(map, 200);

        assertTrue(analysis.endpointCount() >= 2);
        assertFalse(analysis.closed());
    }

    @Test
    public void isEndpointDetectsSingleNeighbor() {
        boolean[][] wall = new boolean[5][5];
        wall[2][1] = true;
        wall[2][2] = true;
        wall[2][3] = true;

        assertTrue(WallEndpointCounter.isEndpoint(wall, 5, 5, 1, 2));
        assertTrue(WallEndpointCounter.isEndpoint(wall, 5, 5, 3, 2));
        assertFalse(WallEndpointCounter.isEndpoint(wall, 5, 5, 2, 2));
    }
}
