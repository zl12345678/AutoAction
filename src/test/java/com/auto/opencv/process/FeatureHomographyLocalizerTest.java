package com.auto.opencv.process;

import com.auto.vision.OpenCvLoader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

import static org.junit.Assert.assertTrue;

public class FeatureHomographyLocalizerTest {
    @BeforeClass
    public static void loadOpenCv() {
        OpenCvLoader.load();
    }

    @Test
    public void localSearchRegionIsSmallerThanFullMap() {
        Mat fullMap = new Mat(300, 300, CvType.CV_8UC1, new Scalar(255));
        drawFeatureRichRegion(fullMap, 40, 40, 120, 120);
        drawFeatureRichRegion(fullMap, 180, 180, 260, 260);

        Mat patch = new Mat(fullMap, new org.opencv.core.Rect(50, 50, 60, 60)).clone();
        Point prior = new Point(80, 80);

        FeatureHomographyLocalizer.SearchRegion region = FeatureHomographyLocalizer.buildSearchRegion(
                fullMap,
                patch,
                prior,
                60
        );

        assertTrue(region.image().cols() < fullMap.cols());
        assertTrue(region.image().rows() < fullMap.rows());
    }

    private static void drawFeatureRichRegion(Mat mat, int x0, int y0, int x1, int y1) {
        Imgproc.rectangle(mat, new Point(x0, y0), new Point(x1, y1), new Scalar(0), -1);
        for (int i = 0; i < 15; i++) {
            Imgproc.circle(
                    mat,
                    new Point(x0 + 8 + i * 5, y0 + 8 + (i % 4) * 7),
                    2,
                    new Scalar(200 - i * 6),
                    -1
            );
        }
    }
}
