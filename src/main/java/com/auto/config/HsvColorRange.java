package com.auto.config;

/**
 * HSV range used to mask out UI markers (player arrow, NPC pins, etc.) before binarization.
 */
public record HsvColorRange(int hueMin, int hueMax, int satMin, int valMin) {
    public HsvColorRange {
        if (hueMin < 0 || hueMax > 180 || hueMin > hueMax) {
            throw new IllegalArgumentException("HSV hue range is invalid");
        }
        if (satMin < 0 || satMin > 255 || valMin < 0 || valMin > 255) {
            throw new IllegalArgumentException("HSV saturation/value must be between 0 and 255");
        }
    }

    public static HsvColorRange orangePlayerMarker() {
        return new HsvColorRange(10, 35, 80, 80);
    }
}
