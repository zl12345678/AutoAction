package com.auto.vision;

import java.awt.image.BufferedImage;

public record PathfindingMapClosureAnalysis(
        boolean closed,
        int walkablePixels,
        int walkableAfterExteriorFlood,
        int exteriorFloodedPixels,
        boolean borderWalkable,
        int leakX,
        int leakY,
        BufferedImage leakPreviewImage,
        BufferedImage sealedPreviewImage,
        String message,
        PathfindingMapClosureFloodTrace floodTrace,
        int wallEndpointCount,
        int wallPixels
) {
    public PathfindingMapClosureAnalysis {
        message = message == null ? "" : message;
    }

    public PathfindingMapClosureAnalysis(
            boolean closed,
            int walkablePixels,
            int walkableAfterExteriorFlood,
            int exteriorFloodedPixels,
            boolean borderWalkable,
            int leakX,
            int leakY,
            BufferedImage leakPreviewImage,
            BufferedImage sealedPreviewImage,
            String message
    ) {
        this(
                closed,
                walkablePixels,
                walkableAfterExteriorFlood,
                exteriorFloodedPixels,
                borderWalkable,
                leakX,
                leakY,
                leakPreviewImage,
                sealedPreviewImage,
                message,
                null,
                0,
                0
        );
    }

    public boolean hasWallEndpoints() {
        return wallEndpointCount > 0;
    }

    public boolean hasLeakPoint() {
        return leakX >= 0 && leakY >= 0;
    }
}
