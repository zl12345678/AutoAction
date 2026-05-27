package com.auto.vision;

import com.auto.config.MapClosureConfig;
import com.auto.config.PointConfig;
import com.auto.config.RegionConfig;
import com.auto.config.VisionConfig;
import com.auto.vision.OpenCvLoader;
import org.junit.Test;
import org.opencv.core.Mat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PathfindingMapLoaderTest {
    @Test
    public void buildsSingleChannelMapWithClosure() {
        OpenCvLoader.load();
        VisionConfig config = new VisionConfig(
                "Game",
                "img/sggd/largeMap_2.bmp",
                "img/arrow_template2.bmp",
                new RegionConfig(0, 0, 10, 10),
                new PointConfig(1, 1),
                50,
                200.0,
                80,
                10.0
        );
        Mat map = PathfindingMapLoader.build(config);
        try {
            assertEquals(1, map.channels());
            assertFalse(map.empty());
        } finally {
            map.release();
        }
    }

    @Test
    public void cacheKeyChangesWithClosure() {
        VisionConfig base = new VisionConfig(
                "Game",
                "map.bmp",
                "arrow.bmp",
                new RegionConfig(0, 0, 10, 10),
                new PointConfig(1, 1),
                50,
                200.0,
                80,
                10.0
        );
        String keyA = PathfindingMapLoader.cacheKey(base);
        VisionConfig sealed = new VisionConfig(
                base.windowTitle(),
                base.mapImage(),
                base.arrowTemplate(),
                base.miniMapRegion(),
                base.target(),
                base.matchAreaSize(),
                base.obstacleThreshold(),
                base.moveStep(),
                base.arriveDistance(),
                base.ocr(),
                base.yolo(),
                base.mapPreprocess(),
                new MapClosureConfig(180, true, 3, 5),
                base.navigation()
        );
        String keyB = PathfindingMapLoader.cacheKey(sealed);
        org.junit.Assert.assertNotEquals(keyA, keyB);
    }
}
