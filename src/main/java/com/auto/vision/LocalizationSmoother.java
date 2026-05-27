package com.auto.vision;

import com.auto.config.NavigationConfig;
import com.auto.opencv.utils.ImageProcessor;
import org.opencv.core.Point;

final class LocalizationSmoother {
    private static final double HIGH_CONFIDENCE_BYPASS = 0.88;
    private static final double LOW_CONFIDENCE_BLEND_THRESHOLD = 0.5;

    private Point smoothedPoint;
    private int predictFramesRemaining;

    LocalizationResult correct(Point rawPoint, double confidence, NavigationConfig navigation, int moveStepPx) {
        int maxPredictFrames = navigation.localizationMaxPredictFrames();
        double alpha = navigation.localizationSmoothingAlpha();
        double maxJump = resolveMaxJump(navigation, moveStepPx);

        if (rawPoint == null) {
            Point predicted = predictWithoutMeasurement(maxPredictFrames);
            if (predicted != null) {
                return new LocalizationResult(
                        predicted,
                        null,
                        false,
                        0.4,
                        LocalizationMethod.SMOOTHED_PREVIOUS,
                        "匹配失败，沿用上次定位"
                );
            }
            return new LocalizationResult(null, null, false, 0.0, LocalizationMethod.NONE, "");
        }

        if (navigation.localizationOutlierRejectionEnabled() && smoothedPoint != null) {
            double jump = ImageProcessor.getDistance(smoothedPoint, rawPoint);
            double allowedJump = maxJump * (1.0 + Math.max(0.0, confidence - navigation.minLocalizationConfidence()));
            if (jump > allowedJump && confidence < HIGH_CONFIDENCE_BYPASS) {
                predictFramesRemaining = maxPredictFrames;
                return new LocalizationResult(
                        smoothedPoint,
                        rawPoint,
                        true,
                        Math.min(confidence, 0.35),
                        LocalizationMethod.OUTLIER_REJECTED,
                        String.format(
                                "原始点偏差 %.0fpx 超过阈值 %.0fpx（置信度 %.2f）",
                                jump,
                                allowedJump,
                                confidence
                        )
                );
            }
        }

        if (smoothedPoint == null || confidence >= LOW_CONFIDENCE_BLEND_THRESHOLD) {
            Point previous = smoothedPoint;
            smoothedPoint = blend(smoothedPoint, rawPoint, alpha);
            predictFramesRemaining = maxPredictFrames;
            String detail = "";
            if (previous != null && ImageProcessor.getDistance(previous, rawPoint) > 5.0) {
                detail = String.format(
                        "平滑定位 (%.0f, %.0f) → (%.0f, %.0f)",
                        rawPoint.x,
                        rawPoint.y,
                        smoothedPoint.x,
                        smoothedPoint.y
                );
            }
            return new LocalizationResult(
                    smoothedPoint,
                    rawPoint,
                    false,
                    confidence,
                    LocalizationMethod.MAP_MATCH,
                    detail
            );
        }

        if (predictFramesRemaining > 0) {
            predictFramesRemaining--;
            return new LocalizationResult(
                    smoothedPoint,
                    rawPoint,
                    false,
                    confidence * 0.5,
                    LocalizationMethod.SMOOTHED_PREVIOUS,
                    "置信度偏低，沿用平滑定位"
            );
        }
        return new LocalizationResult(null, rawPoint, false, 0.0, LocalizationMethod.NONE, "置信度过低，无法更新定位");
    }

    Point lastKnownPoint() {
        return smoothedPoint;
    }

    void reset() {
        smoothedPoint = null;
        predictFramesRemaining = 0;
    }

    private Point predictWithoutMeasurement(int maxPredictFrames) {
        if (predictFramesRemaining > 0 && smoothedPoint != null) {
            predictFramesRemaining--;
            return smoothedPoint;
        }
        return null;
    }

    private static double resolveMaxJump(NavigationConfig navigation, int moveStepPx) {
        if (navigation.maxLocalizationJumpPx() > 0) {
            return navigation.maxLocalizationJumpPx();
        }
        int step = Math.max(1, moveStepPx);
        return step * 3.0;
    }

    private static Point blend(Point previous, Point current, double alpha) {
        if (previous == null) {
            return current;
        }
        double clampedAlpha = Math.max(0.0, Math.min(1.0, alpha));
        return new Point(
                previous.x + (current.x - previous.x) * clampedAlpha,
                previous.y + (current.y - previous.y) * clampedAlpha
        );
    }
}
