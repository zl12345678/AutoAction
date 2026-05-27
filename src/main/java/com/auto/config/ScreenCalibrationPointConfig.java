package com.auto.config;

public record ScreenCalibrationPointConfig(
        double mapX,
        double mapY,
        double screenX,
        double screenY
) {
    public ScreenCalibrationPointConfig {
        if (mapX < 0 || mapY < 0) {
            throw new IllegalArgumentException("map calibration coordinates must be non-negative");
        }
    }
}
