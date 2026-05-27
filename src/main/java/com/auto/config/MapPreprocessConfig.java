package com.auto.config;

import java.util.Objects;

/**
 * Parameters for extracting an in-game map crop and converting it into a pathfinding-ready binary map.
 */
public record MapPreprocessConfig(
        RegionConfig mapRegion,
        boolean binaryEnabled,
        int threshold,
        boolean useOtsu,
        boolean removeOrangeMarkers,
        int orangeHueMin,
        int orangeHueMax,
        int orangeSatMin,
        int orangeValMin,
        double minTemplateScore,
        double minTemplateScoreGap,
        boolean alignmentUseEdges
) {
  public MapPreprocessConfig {
    mapRegion = Objects.requireNonNull(mapRegion, "mapRegion");
    if (threshold < 0 || threshold > 255) {
      throw new IllegalArgumentException("mapPreprocess.threshold must be between 0 and 255");
    }
    if (orangeHueMin < 0 || orangeHueMax > 180 || orangeHueMin > orangeHueMax) {
      throw new IllegalArgumentException("mapPreprocess orange hue range is invalid");
    }
    if (orangeSatMin < 0 || orangeSatMin > 255 || orangeValMin < 0 || orangeValMin > 255) {
      throw new IllegalArgumentException("mapPreprocess orange saturation/value must be between 0 and 255");
    }
    if (minTemplateScore < 0.0 || minTemplateScore > 1.0) {
      throw new IllegalArgumentException("mapPreprocess.minTemplateScore must be between 0 and 1");
    }
    if (minTemplateScoreGap < 0.0 || minTemplateScoreGap > 1.0) {
      throw new IllegalArgumentException("mapPreprocess.minTemplateScoreGap must be between 0 and 1");
    }
  }

  public HsvColorRange orangeRange() {
    return new HsvColorRange(orangeHueMin, orangeHueMax, orangeSatMin, orangeValMin);
  }

  /** Wider HSV range for the gold/yellow player arrow on the minimap. */
  public HsvColorRange playerMarkerRange() {
    return new HsvColorRange(8, 45, 40, 40);
  }

  public MapPreprocessConfig withAlignmentUseEdges(boolean alignmentUseEdges) {
    return new MapPreprocessConfig(
        mapRegion,
        binaryEnabled,
        threshold,
        useOtsu,
        removeOrangeMarkers,
        orangeHueMin,
        orangeHueMax,
        orangeSatMin,
        orangeValMin,
        minTemplateScore,
        minTemplateScoreGap,
        alignmentUseEdges
    );
  }

  public static MapPreprocessConfig defaults() {
    return new MapPreprocessConfig(
        new RegionConfig(0, 0, 1600, 900),
        true,
        127,
        false,
        true,
        10,
        35,
        80,
        80,
        0.48,
        0.08,
        true
    );
  }
}
