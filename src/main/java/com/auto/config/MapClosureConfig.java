package com.auto.config;

/**
 * Parameters for testing whether a pathfinding map is boundary-closed and previewing seal operations.
 */
public record MapClosureConfig(
        double walkableThreshold,
        boolean sealExterior,
        int sealBorderWidth,
        int morphCloseKernelSize
) {
    public MapClosureConfig {
        if (walkableThreshold < 0 || walkableThreshold > 255) {
            throw new IllegalArgumentException("mapClosure.walkableThreshold must be between 0 and 255");
        }
        if (sealBorderWidth < 0) {
            throw new IllegalArgumentException("mapClosure.sealBorderWidth must be >= 0");
        }
        if (morphCloseKernelSize < 0) {
            throw new IllegalArgumentException("mapClosure.morphCloseKernelSize must be >= 0");
        }
    }

    public static MapClosureConfig defaults() {
        return new MapClosureConfig(200.0, true, 2, 3);
    }
}
