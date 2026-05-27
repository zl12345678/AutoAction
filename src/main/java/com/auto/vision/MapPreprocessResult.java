package com.auto.vision;

import java.awt.image.BufferedImage;

public record MapPreprocessResult(
        BufferedImage originalMapImage,
        BufferedImage pathfindingMapImage,
        String message
) {
    public MapPreprocessResult {
        message = message == null ? "" : message;
    }

    public boolean success() {
        return originalMapImage != null && pathfindingMapImage != null;
    }
}
