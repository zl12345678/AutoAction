package com.auto.config;

public record RegionConfig(int x, int y, int width, int height) {
    public RegionConfig {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Region width and height must be positive");
        }
    }
}
