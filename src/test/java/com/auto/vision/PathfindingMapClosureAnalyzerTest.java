package com.auto.vision;

import com.auto.config.MapClosureConfig;
import com.auto.opencv.utils.ImageProcessor;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PathfindingMapClosureAnalyzerTest {
    private final PathfindingMapClosureAnalyzer analyzer = new PathfindingMapClosureAnalyzer();

    @Test
    public void detectsEnclosedInteriorAsClosed() {
        OpenCvLoader.load();
        Mat map = new Mat(80, 80, CvType.CV_8UC1, new Scalar(0));
        Imgproc.rectangle(map, new org.opencv.core.Point(10, 10), new org.opencv.core.Point(69, 69), new Scalar(255), 1);

        PathfindingMapClosureAnalysis analysis = analyzer.analyze(
                ImageProcessor.matToBufferedImage(map),
                MapClosureConfig.defaults()
        );

        assertTrue(analysis.closed());
        assertEquals(0, analysis.wallEndpointCount());
        assertNotNull(analysis.leakPreviewImage());
    }

    @Test
    public void detectsLeakPointWhenTopFloodReachesInterior() {
        OpenCvLoader.load();
        Mat map = new Mat(60, 60, CvType.CV_8UC1, new Scalar(255));
        Imgproc.rectangle(map, new org.opencv.core.Point(5, 5), new org.opencv.core.Point(54, 54), new Scalar(0), -1);
        for (int y = 0; y <= 5; y++) {
            for (int x = 25; x <= 35; x++) {
                map.put(y, x, 0);
            }
        }

        PathfindingMapClosureAnalysis analysis = analyzer.analyze(
                ImageProcessor.matToBufferedImage(map),
                MapClosureConfig.defaults()
        );

        assertFalse(analysis.closed());
        assertTrue(analysis.hasLeakPoint());
    }

    @Test
    public void userFragmentedMapWithBorderWalkableIsNotClosed() {
        OpenCvLoader.load();
        String path = System.getProperty(
                "closureTestImage",
                "C:/Users/admin/.cursor/projects/e-work-AutoAction/assets/c__Users_admin_AppData_Roaming_Cursor_User_workspaceStorage_79a8bd330c52da232aaa4b0141216337_images_______-4ebae87d-ddee-4585-8272-1634f7a929ff.png"
        );
        java.io.File file = new java.io.File(path);
        if (!file.isFile()) {
            return;
        }
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(file);
            PathfindingMapClosureAnalysis analysis = analyzer.analyze(image, MapClosureConfig.defaults());
            System.out.println("user image: closed=" + analysis.closed()
                    + " borderWalkable=" + analysis.borderWalkable()
                    + " flooded=" + analysis.exteriorFloodedPixels()
                    + " msg=" + analysis.message());
            assertFalse("Exterior and interior black should connect: " + analysis.message(), analysis.closed());
        } catch (java.io.IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Test
    public void borderWalkableWithOpenOutlineIsNotClosed() {
        OpenCvLoader.load();
        Mat map = new Mat(80, 80, CvType.CV_8UC1, new Scalar(0));
        Imgproc.rectangle(map, new org.opencv.core.Point(20, 20), new org.opencv.core.Point(59, 59), new Scalar(255), 1);
        for (int x = 20; x <= 59; x++) {
            map.put(20, x, 0);
        }

        PathfindingMapClosureAnalysis analysis = analyzer.analyze(
                ImageProcessor.matToBufferedImage(map),
                MapClosureConfig.defaults()
        );

        assertFalse(analysis.closed());
        assertTrue(analysis.borderWalkable());
    }

    @Test
    public void detectsSealedInteriorAsClosedWhenBorderHasWalkableMargin() {
        OpenCvLoader.load();
        Mat map = new Mat(100, 100, CvType.CV_8UC1, new Scalar(0));
        Imgproc.rectangle(map, new org.opencv.core.Point(20, 20), new org.opencv.core.Point(79, 79), new Scalar(255), 1);

        PathfindingMapClosureAnalysis analysis = analyzer.analyze(
                ImageProcessor.matToBufferedImage(map),
                MapClosureConfig.defaults()
        );

        assertTrue(analysis.closed());
        assertEquals(0, analysis.wallEndpointCount());
        assertTrue(analysis.borderWalkable());
    }

    @Test
    public void buildsRowByRowFloodTrace() {
        OpenCvLoader.load();
        Mat map = new Mat(60, 60, CvType.CV_8UC1, new Scalar(255));
        Imgproc.rectangle(map, new org.opencv.core.Point(5, 5), new org.opencv.core.Point(54, 54), new Scalar(0), -1);
        for (int y = 0; y <= 5; y++) {
            for (int x = 25; x <= 35; x++) {
                map.put(y, x, 0);
            }
        }

        PathfindingMapClosureAnalysis analysis = analyzer.analyze(
                ImageProcessor.matToBufferedImage(map),
                MapClosureConfig.defaults()
        );

        assertNotNull(analysis.floodTrace());
        PathfindingMapClosureFloodTrace trace = analysis.floodTrace();
        assertTrue(trace.rows() > 0);
        assertTrue(trace.floodedCountAtRow(trace.rows() - 1) >= trace.floodedCountAtRow(0));
        assertNotNull(trace.render(0));
        assertNotNull(trace.render(trace.rows() - 1));
        PathfindingMapClosureFloodTrace.RowSpanInfo span = trace.rowSpan(0);
        assertTrue(span.summary().contains("行 0"));
    }

    @Test
    public void rowSpanReportsStartEndDistance() {
        OpenCvLoader.load();
        Mat map = new Mat(60, 60, CvType.CV_8UC1, new Scalar(255));
        Imgproc.rectangle(map, new org.opencv.core.Point(5, 5), new org.opencv.core.Point(54, 54), new Scalar(0), -1);
        for (int y = 0; y <= 5; y++) {
            for (int x = 25; x <= 35; x++) {
                map.put(y, x, 0);
            }
        }

        PathfindingMapClosureAnalysis analysis = analyzer.analyze(
                ImageProcessor.matToBufferedImage(map),
                MapClosureConfig.defaults()
        );

        PathfindingMapClosureFloodTrace trace = analysis.floodTrace();
        assertNotNull(trace);
        assertTrue(trace.globalDistance() > 0);
        assertTrue(trace.globalSpanSummary().contains("泛洪首点"));
        PathfindingMapClosureFloodTrace.RowSpanInfo span = trace.rowSpan(5);
        assertTrue(span.floodedStartX() >= 0);
        assertTrue(span.floodedEndX() >= span.floodedStartX());
    }

    @Test
    public void leakPreviewDrawsVisibleOverlayOnGrayscaleMap() {
        OpenCvLoader.load();
        Mat map = new Mat(40, 40, CvType.CV_8UC1, new Scalar(0));
        Imgproc.rectangle(map, new org.opencv.core.Point(10, 10), new org.opencv.core.Point(29, 29), new Scalar(255), 2);

        PathfindingMapClosureAnalysis analysis = analyzer.analyze(
                ImageProcessor.matToBufferedImage(map),
                MapClosureConfig.defaults()
        );

        BufferedImage preview = analysis.leakPreviewImage();
        assertNotNull(preview);
        assertTrue("Preview must use ARGB for overlays", preview.getType() == BufferedImage.TYPE_INT_ARGB);
        int rgb = preview.getRGB(0, 0);
        int alpha = (rgb >> 24) & 0xFF;
        int red = (rgb >> 16) & 0xFF;
        assertTrue("Flooded border pixel should show red overlay", red > 0 || alpha < 255);
    }

    @Test
    public void reportsNotClosedWhenExteriorAndInteriorBlackAreConnected() {
        OpenCvLoader.load();
        Mat map = new Mat(60, 60, CvType.CV_8UC1, new Scalar(0));

        PathfindingMapClosureAnalysis analysis = analyzer.analyze(
                ImageProcessor.matToBufferedImage(map),
                MapClosureConfig.defaults()
        );

        assertFalse(analysis.closed());
    }
}
