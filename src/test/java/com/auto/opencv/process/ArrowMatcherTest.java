package com.auto.opencv.process;

import com.auto.vision.OpenCvLoader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import static org.junit.Assert.assertTrue;

public class ArrowMatcherTest {
    @BeforeClass
    public static void loadOpenCv() {
        OpenCvLoader.load();
    }

    @Test
    public void confidenceDecreasesAsShapeDistanceIncreases() {
        assertTrue(ArrowMatcher.confidenceFromShapeDistance(0.0) > ArrowMatcher.confidenceFromShapeDistance(0.4));
        assertTrue(ArrowMatcher.confidenceFromShapeDistance(0.79) > 0.0);
        assertTrue(ArrowMatcher.confidenceFromShapeDistance(0.81) == 0.0);
    }

    @Test
    public void matchWithConfidenceReturnsValueBetweenZeroAndOne() {
        Mat miniMap = new Mat(new Size(120, 120), org.opencv.core.CvType.CV_8UC3, new Scalar(20, 20, 20));
        Imgproc.rectangle(miniMap, new org.opencv.core.Point(40, 40), new org.opencv.core.Point(80, 80), new Scalar(0, 140, 255), -1);
        Mat template = miniMap.submat(new org.opencv.core.Rect(40, 40, 40, 40)).clone();

        ArrowMatchResult result = new ArrowMatcher().matchWithConfidence(miniMap, template, 10, 25, 100, 100);

        if (result.found()) {
            assertTrue(result.confidence() >= 0.0);
            assertTrue(result.confidence() <= 1.0);
        }
    }
}
