package com.auto.vision;

import com.auto.config.ScreenCalibrationConfig;
import com.auto.config.ScreenCalibrationPointConfig;
import org.junit.Test;
import org.opencv.core.Point;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScreenMapperTest {
    @org.junit.BeforeClass
    public static void loadOpenCv() {
        OpenCvLoader.load();
    }

    @Test
    public void requiresThreePointsToEnable() {
        ScreenMapper mapper = new ScreenMapper(new ScreenCalibrationConfig(
                true,
                List.of(
                        new ScreenCalibrationPointConfig(0, 0, 100, 100),
                        new ScreenCalibrationPointConfig(10, 0, 200, 100)
                )
        ));

        assertFalse(mapper.enabled());
    }

    @Test
    public void mapsKnownPointWithAffineTransform() {
        ScreenMapper mapper = new ScreenMapper(new ScreenCalibrationConfig(
                true,
                List.of(
                        new ScreenCalibrationPointConfig(0, 0, 100, 200),
                        new ScreenCalibrationPointConfig(100, 0, 300, 200),
                        new ScreenCalibrationPointConfig(0, 100, 100, 400)
                )
        ));

        assertTrue(mapper.enabled());
        Point mapped = mapper.mapToScreen(100, 100);
        assertNotNull(mapped);
        assertEquals(300.0, mapped.x, 1.0);
        assertEquals(400.0, mapped.y, 1.0);
    }
}
