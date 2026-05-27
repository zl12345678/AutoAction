package com.auto.opencv.process;

import com.auto.vision.OpenCvLoader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PathPlannerTest {
    @BeforeClass
    public static void loadOpenCv() {
        OpenCvLoader.load();
    }

    @Test
    public void findsPathOnOpenGrid() {
        Mat map = Mat.zeros(5, 5, CvType.CV_8UC1);

        int[][] path = new PathPlanner(map, new Point(0, 0), new Point(4, 4), 200.0).findRawPath();

        assertTrue(path.length > 0);
        assertEquals(0, path[0][0]);
        assertEquals(4, path[path.length - 1][0]);
    }

    @Test
    public void returnsEmptyPathWhenBlocked() {
        Mat map = Mat.zeros(5, 5, CvType.CV_8UC1);
        for (int y = 0; y < 5; y++) {
            map.put(y, 2, 255);
        }

        int[][] path = new PathPlanner(map, new Point(0, 2), new Point(4, 2), 200.0).findRawPath();

        assertEquals(0, path.length);
    }

    @Test
    public void simplifiesCollinearPoints() {
        List<Point> simplified = PathPlanner.simplifyPath(List.of(
                new Point(0, 0),
                new Point(1, 1),
                new Point(2, 2),
                new Point(2, 3)
        ));

        assertEquals(3, simplified.size());
        assertEquals(0.0, simplified.get(0).x, 0.0);
        assertEquals(0.0, simplified.get(0).y, 0.0);
        assertEquals(2.0, simplified.get(1).x, 0.0);
        assertEquals(2.0, simplified.get(1).y, 0.0);
        assertEquals(2.0, simplified.get(2).x, 0.0);
        assertEquals(3.0, simplified.get(2).y, 0.0);
    }
}
