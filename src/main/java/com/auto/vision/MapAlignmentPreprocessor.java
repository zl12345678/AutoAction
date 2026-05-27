package com.auto.vision;

import com.auto.config.MapPreprocessConfig;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

public final class MapAlignmentPreprocessor {
  private static final int MIN_COMPONENT_AREA = 12;

  public enum AlignmentFeatureMode {
    AUTO,
    PATHFINDING_BOUNDARY,
    EDGES,
    BINARY
  }

  private MapAlignmentPreprocessor() {
  }

  public static Mat prepareLargeMap(Mat image, MapPreprocessConfig config) {
    return prepareLargeMap(image, config, AlignmentFeatureMode.AUTO);
  }

  public static Mat prepareLargeMap(Mat image, MapPreprocessConfig config, AlignmentFeatureMode mode) {
    if (image == null || image.empty()) {
      throw new IllegalArgumentException("large map must not be empty");
    }
    Mat binary = extractLargeMapBinary(image, config);
    return applyLargeMapMode(binary, config, mode);
  }

  public static AlignmentFeatureMode resolveEffectiveMode(
      Mat largeMap,
      MapPreprocessConfig config,
      AlignmentFeatureMode mode
  ) {
    if (mode != AlignmentFeatureMode.AUTO) {
      return mode;
    }
    return resolveLargeMapModeFromBinary(extractLargeMapBinary(largeMap, config), config);
  }

  public static List<AlignmentFeatureMode> alignmentModesForLocalization(Mat largeMap, MapPreprocessConfig config) {
    return List.of(AlignmentFeatureMode.BINARY);
  }

  /** Grayscale map for ORB/SIFT (retains texture; avoids sparse binary/edge maps). */
  public static Mat prepareLargeMapForFeatureMatching(Mat image, MapPreprocessConfig config) {
    if (image == null || image.empty()) {
      throw new IllegalArgumentException("large map must not be empty");
    }
    return enhanceForFeatureDetection(toGrayscale(image));
  }

  /** Minimap grayscale for ORB/SIFT after marker removal. */
  public static Mat prepareMinimapPatchForFeatureMatching(
      Mat patch,
      MapPreprocessConfig config,
      Point markerInPatch
  ) {
    if (patch == null || patch.empty()) {
      throw new IllegalArgumentException("minimap patch must not be empty");
    }
    if (patch.channels() == 1) {
      return patch.clone();
    }
    Mat working = patch.clone();
    if (config.removeOrangeMarkers()) {
      MapMarkerRemover.removeColorRange(working, config.orangeRange());
    }
    if (markerInPatch != null) {
      Mat markerMask = MapMarkerRemover.buildPlayerMarkerMask(working, config.playerMarkerRange());
      MapMarkerRemover.removeColorRange(working, config.playerMarkerRange());
      MapMarkerRemover.inpaintMarkerMask(working, markerMask);
    }
    return enhanceForFeatureDetection(toGrayscale(working));
  }

  private static Mat toGrayscale(Mat image) {
    if (image.channels() == 1) {
      return image.clone();
    }
    Mat gray = new Mat();
    Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
    return gray;
  }

  private static Mat enhanceForFeatureDetection(Mat gray) {
    Mat enhanced = new Mat();
    Imgproc.createCLAHE(2.0, new Size(8, 8)).apply(gray, enhanced);
    return enhanced;
  }

  public static Mat extractLargeMapBinary(Mat image, MapPreprocessConfig config) {
    return toBinary(image, config);
  }

  public static MinimapPatchCrop cropPatchToExploredRegion(Mat patch, Point markerInPatch) {
    return cropPatchToExploredRegion(patch, markerInPatch, MINIMAP_FOG_THRESHOLD, 6);
  }

  public static MinimapPatchCrop cropPatchToExploredRegion(
      Mat patch,
      Point markerInPatch,
      int fogThreshold,
      int padding
  ) {
    if (patch == null || patch.empty()) {
      throw new IllegalArgumentException("minimap patch must not be empty");
    }
    Rect full = new Rect(0, 0, patch.cols(), patch.rows());
    if (markerInPatch == null || patch.channels() == 1) {
      return new MinimapPatchCrop(patch, markerInPatch, full);
    }
    Mat gray = new Mat();
    Imgproc.cvtColor(patch, gray, Imgproc.COLOR_BGR2GRAY);
    Mat pathfinding = MapMarkerRemover.toPathfindingPolarity(gray, fogThreshold);
    Rect bounds = MapMarkerRemover.boundingBoxOfExploredComponent(pathfinding, markerInPatch, padding);
    double keptRatio = (bounds.width * (double) bounds.height) / (patch.cols() * (double) patch.rows());
    if (bounds.width < 20 || bounds.height < 20 || keptRatio > 0.88) {
      return new MinimapPatchCrop(patch, markerInPatch, full);
    }
    Mat cropped = new Mat(patch, bounds).clone();
    Point croppedMarker = new Point(markerInPatch.x - bounds.x, markerInPatch.y - bounds.y);
    return new MinimapPatchCrop(cropped, croppedMarker, bounds);
  }

  public static Mat prepareMinimapPatch(Mat patch, MapPreprocessConfig config) {
    return prepareMinimapPatch(patch, config, null);
  }

  public static Mat prepareMinimapPatch(Mat patch, MapPreprocessConfig config, Point markerInPatch) {
    return prepareMinimapPatch(patch, config, markerInPatch, AlignmentFeatureMode.AUTO);
  }

  public static Mat prepareMinimapPatch(
      Mat patch,
      MapPreprocessConfig config,
      Point markerInPatch,
      AlignmentFeatureMode mode
  ) {
    if (patch == null || patch.empty()) {
      throw new IllegalArgumentException("minimap patch must not be empty");
    }
    if (patch.channels() == 1) {
      Mat binary = toBinary(patch, config);
      return applyMinimapMode(binary, config, mode);
    }
    return prepareColorMap(patch, config, true, markerInPatch, mode);
  }

  private static Mat prepareColorMap(
      Mat bgrImage,
      MapPreprocessConfig config,
      boolean minimapPatch,
      Point markerInPatch,
      AlignmentFeatureMode mode
  ) {
    Mat working = bgrImage.clone();
    if (config.removeOrangeMarkers()) {
      MapMarkerRemover.removeColorRange(working, config.orangeRange());
    }
    if (minimapPatch) {
      Mat markerMask = MapMarkerRemover.buildPlayerMarkerMask(working, config.playerMarkerRange());
      MapMarkerRemover.removeColorRange(working, config.playerMarkerRange());
      MapMarkerRemover.inpaintMarkerMask(working, markerMask);
    }

    Mat gray = new Mat();
    Imgproc.cvtColor(working, gray, Imgproc.COLOR_BGR2GRAY);
    if (minimapPatch) {
      return applyMinimapColorMode(gray, config, mode);
    }
    Mat binary = toBinary(gray, config);
    return applyLargeMapMode(binary, config, mode);
  }

  private static AlignmentFeatureMode resolveLargeMapModeFromBinary(Mat binary, MapPreprocessConfig config) {
    if (MapMarkerRemover.isPathfindingBinary(binary)) {
      return AlignmentFeatureMode.BINARY;
    }
    return config.alignmentUseEdges()
        ? AlignmentFeatureMode.EDGES
        : AlignmentFeatureMode.BINARY;
  }

  private static Mat applyLargeMapMode(Mat binary, MapPreprocessConfig config, AlignmentFeatureMode mode) {
    AlignmentFeatureMode effective = mode == AlignmentFeatureMode.AUTO
        ? resolveLargeMapModeFromBinary(binary, config)
        : mode;
    return switch (effective) {
      case PATHFINDING_BOUNDARY -> MapMarkerRemover.thicken(
          MapMarkerRemover.extractWalkableAdjacentBoundary(binary),
          2
      );
      case EDGES -> finalizeAlignmentMat(binary, config);
      case BINARY -> binary;
      case AUTO -> binary;
    };
  }

  private static final int MINIMAP_FOG_THRESHOLD = 55;

  private static Mat applyMinimapMode(Mat binary, MapPreprocessConfig config, AlignmentFeatureMode mode) {
    AlignmentFeatureMode effective = mode == AlignmentFeatureMode.AUTO
        ? resolveLargeMapModeFromBinary(binary, config)
        : mode;
    if (effective == AlignmentFeatureMode.BINARY) {
      return binary;
    }
    if (effective == AlignmentFeatureMode.EDGES) {
      return finalizeAlignmentMat(binary, config);
    }
    return MapMarkerRemover.thicken(
        MapMarkerRemover.extractWalkableAdjacentBoundary(binary),
        2
    );
  }

  private static Mat applyMinimapColorMode(Mat gray, MapPreprocessConfig config, AlignmentFeatureMode mode) {
    Mat pathfindingStyle = MapMarkerRemover.toPathfindingPolarity(gray, MINIMAP_FOG_THRESHOLD);
    AlignmentFeatureMode effective = mode == AlignmentFeatureMode.AUTO
        ? resolveLargeMapModeFromBinary(pathfindingStyle, config)
        : mode;
    if (effective == AlignmentFeatureMode.EDGES) {
      return finalizeAlignmentMat(pathfindingStyle, config);
    }
    if (effective == AlignmentFeatureMode.PATHFINDING_BOUNDARY) {
      return MapMarkerRemover.thicken(
          MapMarkerRemover.extractWalkableAdjacentBoundary(pathfindingStyle),
          2
      );
    }
    return pathfindingStyle;
  }

  private static Mat toBinary(Mat grayOrBgr, MapPreprocessConfig config) {
    Mat gray = grayOrBgr;
    if (grayOrBgr.channels() > 1) {
      gray = new Mat();
      Imgproc.cvtColor(grayOrBgr, gray, Imgproc.COLOR_BGR2GRAY);
    }
    Mat output = new Mat();
    if (config.binaryEnabled()) {
      if (config.useOtsu()) {
        Imgproc.threshold(gray, output, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
      } else {
        Imgproc.threshold(gray, output, config.threshold(), 255, Imgproc.THRESH_BINARY);
      }
    } else {
      gray.copyTo(output);
    }
    return output;
  }

  private static Mat finalizeAlignmentMat(Mat grayOrBinary, MapPreprocessConfig config) {
    if (!config.alignmentUseEdges()) {
      return grayOrBinary;
    }
    Mat edges = new Mat();
    Imgproc.Canny(grayOrBinary, edges, 50, 150);
    return MapMarkerRemover.thicken(edges, 2);
  }
}
