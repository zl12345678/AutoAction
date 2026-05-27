package com.auto.vision;

import com.auto.config.MapPreprocessConfig;
import com.auto.config.RegionConfig;
import com.auto.opencv.utils.ImageProcessor;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PathfindingMapPreprocessorTest {
    private final PathfindingMapPreprocessor preprocessor = new PathfindingMapPreprocessor();

    @Test
    public void producesBinaryMapFromSyntheticCapture() {
        OpenCvLoader.load();
        Mat capture = new Mat(120, 120, CvType.CV_8UC3, new Scalar(30, 30, 30));
        Imgproc.rectangle(capture, new org.opencv.core.Point(20, 20), new org.opencv.core.Point(100, 100), new Scalar(220, 220, 220), -1);
        MapPreprocessConfig base = MapPreprocessConfig.defaults();
        MapPreprocessConfig config = new MapPreprocessConfig(
                new RegionConfig(10, 10, 100, 100),
                base.binaryEnabled(),
                100,
                base.useOtsu(),
                false,
                base.orangeHueMin(),
                base.orangeHueMax(),
                base.orangeSatMin(),
                base.orangeValMin(),
                base.minTemplateScore(),
                base.minTemplateScoreGap(),
                base.alignmentUseEdges()
        );

        MapPreprocessResult result = preprocessor.process(ImageProcessor.matToBufferedImage(capture), config);

        assertTrue(result.success());
        assertEquals(100, result.originalMapImage().getWidth());
        assertEquals(100, result.pathfindingMapImage().getWidth());
        assertNotNull(result.message());
    }
}
