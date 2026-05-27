package com.auto.config;

import java.util.List;
import java.util.Objects;

public record YoloConfig(
        boolean enabled,
        String modelPath,
        String labelsPath,
        int inputWidth,
        int inputHeight,
        double confidenceThreshold,
        double iouThreshold,
        int maxDetections,
        RegionConfig region,
        List<String> classesOfInterest
) {
    public static YoloConfig disabled() {
        return new YoloConfig(false, "", "", 640, 640, 0.25, 0.45, 50, null, List.of());
    }

    public YoloConfig {
        modelPath = modelPath == null ? "" : modelPath;
        labelsPath = labelsPath == null ? "" : labelsPath;
        if (inputWidth <= 0 || inputHeight <= 0) {
            throw new IllegalArgumentException("yolo input size must be positive");
        }
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw new IllegalArgumentException("yolo confidenceThreshold must be between 0 and 1");
        }
        if (iouThreshold < 0.0 || iouThreshold > 1.0) {
            throw new IllegalArgumentException("yolo iouThreshold must be between 0 and 1");
        }
        if (maxDetections <= 0) {
            throw new IllegalArgumentException("yolo maxDetections must be positive");
        }
        Objects.requireNonNull(classesOfInterest, "classesOfInterest");
        classesOfInterest = List.copyOf(classesOfInterest);
    }
}
