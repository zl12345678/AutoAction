package com.auto.vision;

import org.opencv.core.Point;

import java.util.Arrays;

public record NavigationStep(
        Point currentMapPoint,
        Point targetMapPoint,
        Point screenPoint,
        int[][] path,
        boolean arrived,
        double localizationConfidence,
        LocalizationMethod localizationMethod
) {
    public NavigationStep(
            Point currentMapPoint,
            Point targetMapPoint,
            Point screenPoint,
            int[][] path,
            boolean arrived
    ) {
        this(currentMapPoint, targetMapPoint, screenPoint, path, arrived, 1.0, LocalizationMethod.MAP_MATCH);
    }

    public String summary() {
        return "current=" + currentMapPoint
                + ", target=" + targetMapPoint
                + ", screen=" + screenPoint
                + ", arrived=" + arrived
                + ", confidence=" + localizationConfidence
                + ", method=" + localizationMethod
                + ", path=" + Arrays.deepToString(path);
    }
}
