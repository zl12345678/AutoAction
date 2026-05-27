package com.auto.detection;

import java.awt.Rectangle;
import java.util.Objects;

public record DetectedObject(
        String label,
        double score,
        Rectangle bounds,
        int classId
) {
    public DetectedObject {
        label = label == null ? "" : label;
        bounds = new Rectangle(Objects.requireNonNull(bounds, "bounds"));
    }
}
