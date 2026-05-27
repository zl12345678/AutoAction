package com.auto.vision;

import org.junit.Test;
import org.opencv.core.Point;

import static org.junit.Assert.assertEquals;

public class OpenCvNavigationAnalyzerWaypointTest {
    @Test
    public void chooseNextMapPointUsesStartIndex() {
        int[][] path = {
                {10, 10},
                {20, 10},
                {80, 10},
                {200, 200}
        };

        Point first = OpenCvNavigationAnalyzer.chooseNextMapPoint(
                new Point(10, 10),
                new Point(200, 200),
                path,
                15.0,
                0
        );
        Point later = OpenCvNavigationAnalyzer.chooseNextMapPoint(
                new Point(75, 10),
                new Point(200, 200),
                path,
                15.0,
                2
        );

        assertEquals(80.0, first.x, 0.01);
        assertEquals(200.0, later.x, 0.01);
    }
}
