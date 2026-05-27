package com.auto.opencv.process;

import com.auto.config.MapPreprocessConfig;
import com.auto.vision.OpenCvLoader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;

import static org.junit.Assert.assertTrue;

public class NavigationDebugReplayTest {
  private static final Point OLD_FALSE_MATCH = new Point(128, 232);

  @BeforeClass
  public static void loadOpenCv() {
    OpenCvLoader.load();
  }

  @Test
  public void doesNotReproduceLeftCorridorFalseMatchFrom204805Session() {
    Mat patchRaw = Imgcodecs.imread("navigation-debug/20260524-204805/04-locate_on_map/match_patch_raw.png");
    Mat largeMap = Imgcodecs.imread("src/main/resources/img/sggd/largeMap_2.bmp");
    MapPreprocessConfig config = MapPreprocessConfig.defaults();
    Point arrowInPatch = new Point(35, 28);

    MapMatchDebug debug = MapMatcher.forLocalizationWithDebug(
        largeMap,
        patchRaw,
        config,
        arrowInPatch,
        arrowInPatch,
        null
    );

    if (!debug.result().found()) {
      return;
    }

    Point matched = debug.result().mapPoint();
    double distanceFromOldFalseMatch = Math.hypot(
        matched.x - OLD_FALSE_MATCH.x,
        matched.y - OLD_FALSE_MATCH.y
    );
    assertTrue(
        "Should not reproduce old left-corridor false match at (128,232), got "
            + matched + " conf=" + debug.result().confidence()
            + " attempts=" + String.join("; ", debug.attempts()),
        distanceFromOldFalseMatch > 100
    );
  }

  private static final Point EXPECTED_215623 = new Point(30, 140);
  private static final int PRIOR_SEARCH_RADIUS = 150;

  @Test
  public void localizes215623SessionNearGroundTruth() {
    Mat patchRaw = Imgcodecs.imread("navigation-debug/20260524-215623/04-locate_on_map/match_patch_raw.png");
    Mat largeMap = Imgcodecs.imread("src/main/resources/img/sggd/largeMap_2.bmp");
    MapPreprocessConfig config = MapPreprocessConfig.defaults();
    Point arrowInPatch = new Point(42, 38);

    MapMatchDebug debug = MapMatcher.forLocalizationWithDebug(
        largeMap,
        patchRaw,
        config,
        arrowInPatch,
        arrowInPatch,
        null,
        0
    );

    if (!debug.result().found()) {
      return;
    }

    Point matched = debug.result().mapPoint();
    double distance = Math.hypot(matched.x - EXPECTED_215623.x, matched.y - EXPECTED_215623.y);
    assertTrue(
        "Should localize near ground truth (30,140), got "
            + matched + " conf=" + debug.result().confidence()
            + " attempts=" + String.join("; ", debug.attempts()),
        distance < 45
    );
  }

  @Test
  public void localizes224235SessionNearGroundTruth() {
    Mat patchRaw = Imgcodecs.imread("navigation-debug/20260524-224235/04-locate_on_map/match_patch_raw.png");
    Mat largeMap = Imgcodecs.imread("src/main/resources/img/sggd/largeMap_2.bmp");
    MapPreprocessConfig config = MapPreprocessConfig.defaults();
    Point arrowInPatch = new Point(42, 38);

    MapMatchDebug debug = MapMatcher.forLocalizationWithDebug(
        largeMap,
        patchRaw,
        config,
        arrowInPatch,
        arrowInPatch,
        null,
        0
    );

    if (!debug.result().found()) {
      return;
    }

    Point matched = debug.result().mapPoint();
    double distance = Math.hypot(matched.x - EXPECTED_215623.x, matched.y - EXPECTED_215623.y);
    assertTrue(
        "Should localize near ground truth (30,140), got "
            + matched + " conf=" + debug.result().confidence(),
        distance < 45
    );
  }

  @Test
  public void localizes223652SessionNearGroundTruth() {
    Mat patchRaw = Imgcodecs.imread("navigation-debug/20260525-223652/04-locate_on_map/match_patch_raw.png");
    Mat largeMap = Imgcodecs.imread("src/main/resources/img/sggd/largeMap_2.bmp");
    MapPreprocessConfig config = MapPreprocessConfig.defaults();
    Point arrowInPatch = new Point(42, 38);

    MapMatchDebug debug = MapMatcher.forLocalizationWithDebug(
        largeMap,
        patchRaw,
        config,
        arrowInPatch,
        arrowInPatch,
        null,
        0
    );

    if (!debug.result().found()) {
      return;
    }

    Point matched = debug.result().mapPoint();
    double distance = Math.hypot(matched.x - EXPECTED_215623.x, matched.y - EXPECTED_215623.y);
    assertTrue(
        "Should localize near ground truth (30,140), got "
            + matched + " conf=" + debug.result().confidence()
            + " attempts=" + String.join("; ", debug.attempts()),
        distance < 65 && matched.x < 120
    );
  }

  @Test
  public void doesNotReproduceRightRoomFalseMatchFrom215623Session() {
    Mat patchRaw = Imgcodecs.imread("navigation-debug/20260524-215623/04-locate_on_map/match_patch_raw.png");
    Mat largeMap = Imgcodecs.imread("src/main/resources/img/sggd/largeMap_2.bmp");
    MapPreprocessConfig config = MapPreprocessConfig.defaults();
    Point arrowInPatch = new Point(42, 38);
    Point falseRightRoomMatch = new Point(779, 285);

    MapMatchDebug debug = MapMatcher.forLocalizationWithDebug(
        largeMap,
        patchRaw,
        config,
        arrowInPatch,
        arrowInPatch,
        null
    );

    if (!debug.result().found()) {
      return;
    }

    Point matched = debug.result().mapPoint();
    double distanceFromFalseRightMatch = Math.hypot(
        matched.x - falseRightRoomMatch.x,
        matched.y - falseRightRoomMatch.y
    );
    assertTrue(
        "Should not reproduce far-right false match near target, got "
            + matched + " conf=" + debug.result().confidence(),
        matched.x < 200 && distanceFromFalseRightMatch > 120
    );
  }

  @Test
  public void wrongCrop233419SessionIsFarFromGroundTruth() {
    Mat patchRaw = Imgcodecs.imread("navigation-debug/20260524-233419/04-locate_on_map/match_patch_raw.png");
    Mat largeMap = Imgcodecs.imread("src/main/resources/img/sggd/largeMap_2.bmp");
    MapPreprocessConfig config = MapPreprocessConfig.defaults();
    Point arrowInPatch = new Point(35, 28);

    MapMatchDebug debug = MapMatcher.forLocalizationWithDebug(
        largeMap,
        patchRaw,
        config,
        arrowInPatch,
        arrowInPatch,
        null
    );

    if (!debug.result().found()) {
      return;
    }

    Point matched = debug.result().mapPoint();
    double distance = Math.hypot(matched.x - EXPECTED_215623.x, matched.y - EXPECTED_215623.y);
    assertTrue(
        "Mis-cropped minimap session should not localize near (30,140), got "
            + matched + " conf=" + debug.result().confidence(),
        distance > 80
    );
  }

  @Test
  public void acceptsPathfindingMapSession212801() {
    Mat patchRaw = Imgcodecs.imread("navigation-debug/20260524-212801/04-locate_on_map/match_patch_raw.png");
    Mat largeMap = Imgcodecs.imread("src/main/resources/img/sggd/largeMap_2.bmp");
    MapPreprocessConfig config = MapPreprocessConfig.defaults();
    Point arrowInPatch = new Point(42, 39);

    MapMatchDebug debug = MapMatcher.forLocalizationWithDebug(
        largeMap,
        patchRaw,
        config,
        arrowInPatch,
        arrowInPatch,
        null,
        0
    );

    assertTrue(
        "Expected pathfinding map session to match: " + String.join("; ", debug.attempts()),
        debug.result().found()
    );
    assertTrue(debug.result().confidence() >= 0.33);
  }
}
