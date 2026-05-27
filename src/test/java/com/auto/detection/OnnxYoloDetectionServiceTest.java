package com.auto.detection;

import com.auto.config.RegionConfig;
import com.auto.config.YoloConfig;
import org.junit.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OnnxYoloDetectionServiceTest {
    @Test
    public void detectMapsPredictionBackToOriginalWindowCoordinates() {
        BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_3BYTE_BGR);
        YoloConfig config = new YoloConfig(
                true,
                "model.onnx",
                "",
                100,
                100,
                0.25,
                0.45,
                10,
                new RegionConfig(50, 20, 100, 50),
                List.of()
        );
        OnnxYoloDetectionService service = new OnnxYoloDetectionService((currentConfig, input, width, height) -> {
            assertEquals(100, width);
            assertEquals(100, height);
            assertEquals(100 * 100 * 3, input.length);
            return new OnnxYoloDetectionService.RawYoloResult(
                    new float[][]{
                            {20f, 35f, 20f, 10f, 0.90f, 0.80f}
                    },
                    Map.of(0, "npc")
            );
        });

        List<DetectedObject> detections = service.detect(config, source);

        assertEquals(1, detections.size());
        DetectedObject detection = detections.get(0);
        assertEquals("npc", detection.label());
        assertEquals(0, detection.classId());
        assertEquals(new Rectangle(60, 25, 20, 10), detection.bounds());
        assertEquals(0.72, detection.score(), 1e-6);
    }

    @Test
    public void nonMaximumSuppressionKeepsBestBoxPerClass() {
        DetectedObject bestNpc = new DetectedObject("npc", 0.91, new Rectangle(10, 10, 50, 50), 0);
        DetectedObject weakerNpc = new DetectedObject("npc", 0.65, new Rectangle(14, 14, 50, 50), 0);
        DetectedObject drop = new DetectedObject("drop", 0.80, new Rectangle(14, 14, 50, 50), 1);

        List<DetectedObject> kept = OnnxYoloDetectionService.nonMaximumSuppression(
                List.of(bestNpc, weakerNpc, drop),
                0.4,
                10
        );

        assertEquals(2, kept.size());
        assertTrue(kept.contains(bestNpc));
        assertTrue(kept.contains(drop));
    }
}
