package com.auto.opencv.process;

import org.opencv.core.Point;

public record ArrowMatchResult(
        Point center,
        double confidence
) {
    public boolean found() {
        return center != null;
    }
}
