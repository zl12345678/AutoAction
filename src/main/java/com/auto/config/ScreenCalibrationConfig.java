package com.auto.config;

import java.util.List;
import java.util.Objects;

public record ScreenCalibrationConfig(
        boolean enabled,
        List<ScreenCalibrationPointConfig> points
) {
    public ScreenCalibrationConfig {
        points = List.copyOf(Objects.requireNonNullElse(points, List.of()));
    }

    public static ScreenCalibrationConfig disabled() {
        return new ScreenCalibrationConfig(false, List.of());
    }
}
