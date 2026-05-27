package com.auto.vision;

import com.auto.config.AppConfigLoader;
import com.auto.config.RegionConfig;
import com.auto.config.VisionConfig;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OpenCvNavigationAnalyzerTest {
  private static final Path DEBUG_CAPTURE = Path.of(
      "navigation-debug/20260524-212801/01-capture_window/source.png"
  );

  @BeforeClass
  public static void loadOpenCv() {
    OpenCvLoader.load();
  }

  @Test
  public void sampleAnalysisProducesRouteAndPreviewImages() throws Exception {
    Assume.assumeTrue("Requires navigation-debug capture", Files.exists(DEBUG_CAPTURE));

    VisionConfig loaded = new AppConfigLoader().loadFromResource("autoActionConfig.json").vision();

    OpenCvNavigationAnalyzer analyzer = new OpenCvNavigationAnalyzer();
    BufferedImage sample = ImageIO.read(DEBUG_CAPTURE.toFile());
    Rectangle bounds = new Rectangle(0, 0, sample.getWidth(), sample.getHeight());
    int[][] candidateTargets = {
        {435, 160},
        {440, 165},
        {450, 160},
        {480, 200},
    };

    NavigationAnalysis analysis = null;
    for (int[] candidateTarget : candidateTargets) {
      VisionConfig config = new VisionConfig(
          loaded.windowTitle(),
          loaded.mapImage(),
          loaded.arrowTemplate(),
          new RegionConfig(64, 86, 84, 80),
          new com.auto.config.PointConfig(candidateTarget[0], candidateTarget[1]),
          loaded.matchAreaSize(),
          loaded.obstacleThreshold(),
          loaded.moveStep(),
          loaded.arriveDistance(),
          loaded.ocr(),
          loaded.yolo(),
          loaded.mapPreprocess(),
          loaded.mapClosure(),
          loaded.navigation()
      );
      analysis = analyzer.analyzeWindowCapture(
          config,
          sample,
          bounds,
          "navigation-debug/20260524-212801"
      );
      if (analysis.success()) {
        break;
      }
    }

    assertNotNull(analysis);
    assertTrue(
        "Sample analysis failed: " + analysis.message(),
        analysis.success()
    );
    assertNotNull(analysis.arrowCenter());
    assertNotNull(analysis.currentMapPoint());
    assertNotNull(analysis.nextMapPoint());
    assertNotNull(analysis.nextScreenPoint());
    assertNotNull(analysis.miniMapPreviewImage());
    assertNotNull(analysis.mapPreviewImage());
    assertNotNull(analysis.clickPreviewImage());
    assertTrue(analysis.path().length > 0);
  }
}
