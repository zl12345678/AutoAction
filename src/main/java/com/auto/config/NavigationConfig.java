package com.auto.config;

import java.util.Objects;

public record NavigationConfig(
        int tickIntervalMs,
        int stuckTimeoutMs,
        double stuckDistanceThreshold,
        double waypointReachDistance,
        int maxStuckRetries,
        double minLocalizationConfidence,
        double localizationSmoothingAlpha,
        int localizationMaxPredictFrames,
        boolean localizationOutlierRejectionEnabled,
        double maxLocalizationJumpPx,
        ScreenCalibrationConfig screenCalibration
) {
    public NavigationConfig {
        if (tickIntervalMs <= 0) {
            throw new IllegalArgumentException("navigation.tickIntervalMs must be positive");
        }
        if (stuckTimeoutMs <= 0) {
            throw new IllegalArgumentException("navigation.stuckTimeoutMs must be positive");
        }
        if (stuckDistanceThreshold <= 0) {
            throw new IllegalArgumentException("navigation.stuckDistanceThreshold must be positive");
        }
        if (waypointReachDistance <= 0) {
            throw new IllegalArgumentException("navigation.waypointReachDistance must be positive");
        }
        if (maxStuckRetries < 0) {
            throw new IllegalArgumentException("navigation.maxStuckRetries must be non-negative");
        }
        if (minLocalizationConfidence < 0 || minLocalizationConfidence > 1) {
            throw new IllegalArgumentException("navigation.minLocalizationConfidence must be between 0 and 1");
        }
        if (localizationSmoothingAlpha < 0 || localizationSmoothingAlpha > 1) {
            throw new IllegalArgumentException("navigation.localizationSmoothingAlpha must be between 0 and 1");
        }
        if (localizationMaxPredictFrames < 0) {
            throw new IllegalArgumentException("navigation.localizationMaxPredictFrames must be non-negative");
        }
        if (maxLocalizationJumpPx < 0) {
            throw new IllegalArgumentException("navigation.maxLocalizationJumpPx must be non-negative");
        }
        screenCalibration = Objects.requireNonNullElse(screenCalibration, ScreenCalibrationConfig.disabled());
    }

    public static NavigationConfig defaults() {
        return new NavigationConfig(
                400,
                3000,
                8.0,
                15.0,
                3,
                0.45,
                0.35,
                2,
                true,
                0.0,
                ScreenCalibrationConfig.disabled()
        );
    }
}
