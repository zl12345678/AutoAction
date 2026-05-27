package com.auto.opencv.process;

import org.opencv.core.Point;

public record MapMatchResult(
        Point mapPoint,
        double confidence,
        MapMatchMethod method
) {
    public boolean found() {
        return mapPoint != null;
    }

    public static MapMatchResult failed() {
        return new MapMatchResult(null, 0.0, MapMatchMethod.NONE);
    }
}
