package com.auto.vision;

import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PathfindingMapImporterTest {
    @Test
    public void normalizesEditedMapToBinaryGrayscale() {
        OpenCvLoader.load();
        Mat color = new Mat(40, 40, CvType.CV_8UC3, new Scalar(0, 0, 0));
        Imgproc.rectangle(color, new org.opencv.core.Point(5, 5), new org.opencv.core.Point(34, 34), new Scalar(255, 255, 255), 2);
        BufferedImage input = com.auto.opencv.utils.ImageProcessor.matToBufferedImage(color);

        BufferedImage result = PathfindingMapImporter.normalizeEditedMap(input);

        assertEquals(40, result.getWidth());
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, result.getType());
        boolean onlyBinary = true;
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                int value = result.getRaster().getSample(x, y, 0);
                if (value != 0 && value != 255) {
                    onlyBinary = false;
                    break;
                }
            }
        }
        assertTrue(onlyBinary);
    }
}
