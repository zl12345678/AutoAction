package com.auto.vision;

import com.auto.config.MapClosureConfig;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WallEndpointReferenceMapTest {
    private final PathfindingMapClosureAnalyzer analyzer = new PathfindingMapClosureAnalyzer();

  private static final String NOT_CLOSED = "C:/Users/admin/.cursor/projects/e-work-AutoAction/assets/c__Users_admin_AppData_Roaming_Cursor_User_workspaceStorage_79a8bd330c52da232aaa4b0141216337_images_______-a037f6cd-24bb-405a-860c-3345b7b2e415.png";
    private static final String CLOSED = "C:/Users/admin/.cursor/projects/e-work-AutoAction/assets/c__Users_admin_AppData_Roaming_Cursor_User_workspaceStorage_79a8bd330c52da232aaa4b0141216337_images_largeMap_2-c6519de3-4d5e-40e6-97e0-8e3f43e12642.png";

    @Test
    public void referenceNotClosedMap() throws Exception {
        analyzeExpect(NOT_CLOSED, false);
    }

    @Test
    public void referenceClosedMap() throws Exception {
        analyzeExpect(CLOSED, true);
    }

    private void analyzeExpect(String path, boolean expectClosed) throws Exception {
        File file = new File(path);
        if (!file.isFile()) {
            return;
        }
        BufferedImage image = ImageIO.read(file);
        PathfindingMapClosureAnalysis analysis = analyzer.analyze(image, MapClosureConfig.defaults());
        System.out.println(path.substring(path.lastIndexOf('/') + 1)
                + " closed=" + analysis.closed()
                + " endpoints=" + analysis.wallEndpointCount()
                + " walls=" + analysis.wallPixels()
                + " msg=" + analysis.message());
        if (expectClosed) {
            assertTrue("Expected closed: " + analysis.message(), analysis.closed());
        } else {
            assertFalse("Expected not closed: " + analysis.message(), analysis.closed());
        }
    }
}
