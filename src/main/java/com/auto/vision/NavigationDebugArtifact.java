package com.auto.vision;

import java.awt.image.BufferedImage;

public record NavigationDebugArtifact(
        String id,
        String label,
        BufferedImage image
) {
    public NavigationDebugArtifact {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("artifact id is required");
        }
        label = label == null ? id : label;
    }
}
