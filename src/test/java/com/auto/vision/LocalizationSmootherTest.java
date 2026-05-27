package com.auto.vision;

import com.auto.config.NavigationConfig;
import com.auto.config.ScreenCalibrationConfig;
import org.junit.Before;
import org.junit.Test;
import org.opencv.core.Point;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LocalizationSmootherTest {
    private LocalizationSmoother smoother;

    @Before
    public void setUp() {
        smoother = new LocalizationSmoother();
    }

    @Test
    public void acceptsFirstMeasurementWithoutRejection() {
        NavigationConfig navigation = navigationConfig(true, 0.0);
        LocalizationResult result = smoother.correct(new Point(100, 100), 0.7, navigation, 80);

        assertEquals(new Point(100, 100), result.acceptedPoint());
        assertFalse(result.outlierRejected());
        assertEquals(LocalizationMethod.MAP_MATCH, result.method());
    }

    @Test
    public void rejectsLargeJumpWhenOutlierRejectionEnabled() {
        NavigationConfig navigation = navigationConfig(true, 120.0);
        smoother.correct(new Point(100, 100), 0.7, navigation, 80);

        LocalizationResult result = smoother.correct(new Point(400, 100), 0.6, navigation, 80);

        assertEquals(new Point(100, 100), result.acceptedPoint());
        assertTrue(result.outlierRejected());
        assertEquals(LocalizationMethod.OUTLIER_REJECTED, result.method());
        assertNotNull(result.rawPoint());
    }

    @Test
    public void allowsLargeJumpWhenOutlierRejectionDisabled() {
        NavigationConfig navigation = navigationConfig(false, 120.0);
        smoother.correct(new Point(100, 100), 0.7, navigation, 80);

        LocalizationResult result = smoother.correct(new Point(400, 100), 0.6, navigation, 80);

        assertFalse(result.outlierRejected());
        assertTrue(result.acceptedPoint().x > 100.0);
    }

    @Test
    public void usesAutoJumpThresholdFromMoveStep() {
        NavigationConfig navigation = navigationConfig(true, 0.0);
        smoother.correct(new Point(100, 100), 0.7, navigation, 80);

        LocalizationResult result = smoother.correct(new Point(400, 100), 0.6, navigation, 80);

        assertTrue(result.outlierRejected());
    }

    private static NavigationConfig navigationConfig(boolean outlierRejectionEnabled, double maxJumpPx) {
        return new NavigationConfig(
                400,
                3000,
                8.0,
                15.0,
                3,
                0.45,
                0.35,
                2,
                outlierRejectionEnabled,
                maxJumpPx,
                ScreenCalibrationConfig.disabled()
        );
    }
}
