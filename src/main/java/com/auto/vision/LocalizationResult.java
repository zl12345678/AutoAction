package com.auto.vision;

import org.opencv.core.Point;

public record LocalizationResult(
        Point acceptedPoint,
        Point rawPoint,
        boolean outlierRejected,
        double effectiveConfidence,
        LocalizationMethod method,
        String detail
) {
    public LocalizationResult {
        detail = detail == null ? "" : detail;
        if (method == null) {
            method = LocalizationMethod.NONE;
        }
    }
}
