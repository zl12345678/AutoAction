package com.auto.config;

import java.util.Objects;

public record VisionConfig(
        String windowTitle,
        String mapImage,
        String arrowTemplate,
        RegionConfig miniMapRegion,
        PointConfig target,
        int matchAreaSize,
        double obstacleThreshold,
        int moveStep,
        double arriveDistance,
        OcrConfig ocr,
        YoloConfig yolo,
        MapPreprocessConfig mapPreprocess,
        MapClosureConfig mapClosure,
        NavigationConfig navigation
) {
    public VisionConfig(
            String windowTitle,
            String mapImage,
            String arrowTemplate,
            RegionConfig miniMapRegion,
            PointConfig target,
            int matchAreaSize,
            double obstacleThreshold,
            int moveStep,
            double arriveDistance
    ) {
        this(
                windowTitle,
                mapImage,
                arrowTemplate,
                miniMapRegion,
                target,
                matchAreaSize,
                obstacleThreshold,
                moveStep,
                arriveDistance,
                OcrConfig.disabled(),
                YoloConfig.disabled(),
                MapPreprocessConfig.defaults(),
                MapClosureConfig.defaults(),
                NavigationConfig.defaults()
        );
    }

    public VisionConfig(
            String windowTitle,
            String mapImage,
            String arrowTemplate,
            RegionConfig miniMapRegion,
            PointConfig target,
            int matchAreaSize,
            double obstacleThreshold,
            int moveStep,
            double arriveDistance,
            OcrConfig ocr,
            YoloConfig yolo,
            MapPreprocessConfig mapPreprocess,
            MapClosureConfig mapClosure
    ) {
        this(
                windowTitle,
                mapImage,
                arrowTemplate,
                miniMapRegion,
                target,
                matchAreaSize,
                obstacleThreshold,
                moveStep,
                arriveDistance,
                ocr,
                yolo,
                mapPreprocess,
                mapClosure,
                NavigationConfig.defaults()
        );
    }

    public VisionConfig {
        requireText(windowTitle, "vision.windowTitle");
        requireText(mapImage, "vision.mapImage");
        requireText(arrowTemplate, "vision.arrowTemplate");
        miniMapRegion = Objects.requireNonNull(miniMapRegion, "miniMapRegion");
        target = Objects.requireNonNull(target, "target");
        ocr = Objects.requireNonNull(ocr, "ocr");
        yolo = Objects.requireNonNull(yolo, "yolo");
        mapPreprocess = Objects.requireNonNull(mapPreprocess, "mapPreprocess");
        mapClosure = Objects.requireNonNull(mapClosure, "mapClosure");
        navigation = Objects.requireNonNull(navigation, "navigation");
        if (matchAreaSize <= 0) {
            throw new IllegalArgumentException("vision.matchAreaSize must be positive");
        }
        if (moveStep <= 0) {
            throw new IllegalArgumentException("vision.moveStep must be positive");
        }
        if (arriveDistance <= 0) {
            throw new IllegalArgumentException("vision.arriveDistance must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
