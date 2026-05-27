package com.auto.capture;

import com.auto.config.MapClosureConfig;
import com.auto.config.MapPreprocessConfig;
import com.auto.config.OcrConfig;
import com.auto.config.OcrRegionConfig;
import com.auto.config.OcrRegionSource;
import com.auto.config.PointConfig;
import com.auto.config.RegionConfig;
import com.auto.config.VisionConfig;
import com.auto.config.YoloConfig;
import com.auto.detection.DetectedObject;
import com.auto.detection.ObjectDetectionService;
import com.auto.ocr.OcrResult;
import com.auto.ocr.OcrService;
import com.auto.vision.OpenCvLoader;
import com.auto.vision.OpenCvNavigationAnalyzer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CaptureAnalysisServiceTest {
    @BeforeClass
    public static void loadOpenCv() {
        OpenCvLoader.load();
    }

    @Test
    public void analyzeSampleKeepsNavigationWhenYoloFails() {
        OcrService ocrService = (config, windowImage, miniMapImage) -> List.of(
                new OcrResult("coords", "123,456", 96.0, new Rectangle(1200, 24, 160, 40), windowImage)
        );
        ObjectDetectionService detectionService = (config, sourceImage) -> {
            throw new IllegalStateException("YOLO model file not found: C:\\missing\\ui-detector.onnx");
        };
        CaptureAnalysisService service = new CaptureAnalysisService(
                new OpenCvNavigationAnalyzer(),
                ocrService,
                detectionService
        );

        CaptureAnalysis analysis = service.analyzeSample(sampleConfig(true, true));

        assertNotNull(analysis.navigationAnalysis());
        assertNotNull(analysis.sourceImage());
        assertEquals(1, analysis.ocrResults().size());
        assertTrue(analysis.detections().isEmpty());
        assertTrue(analysis.diagnostics().stream().anyMatch(message -> message.contains("OCR 已运行")));
        assertTrue(analysis.diagnostics().stream().anyMatch(message -> message.contains("YOLO 运行失败")));
    }

    @Test
    public void analyzeSampleKeepsNavigationWhenOcrFails() {
        OcrService ocrService = (config, windowImage, miniMapImage) -> {
            throw new IllegalStateException("tessdata missing");
        };
        ObjectDetectionService detectionService = (config, sourceImage) -> List.of(
                new DetectedObject("npc", 0.88, new Rectangle(800, 400, 50, 70), 0)
        );
        CaptureAnalysisService service = new CaptureAnalysisService(
                new OpenCvNavigationAnalyzer(),
                ocrService,
                detectionService
        );

        CaptureAnalysis analysis = service.analyzeSample(sampleConfig(true, true));

        assertNotNull(analysis.navigationAnalysis());
        assertNotNull(analysis.sourceImage());
        assertTrue(analysis.ocrResults().isEmpty());
        assertEquals(1, analysis.detections().size());
        assertTrue(analysis.diagnostics().stream().anyMatch(message -> message.contains("OCR 运行失败")));
        assertTrue(analysis.diagnostics().stream().anyMatch(message -> message.contains("YOLO 已运行")));
        assertFalse(analysis.combinedPreviewImage().getWidth() <= 0);
    }

    private static VisionConfig sampleConfig(boolean ocrEnabled, boolean yoloEnabled) {
        OcrConfig ocrConfig = new OcrConfig(
                ocrEnabled,
                "eng",
                7,
                "0123456789, ",
                List.of(new OcrRegionConfig(
                        "coords",
                        OcrRegionSource.WINDOW,
                        new RegionConfig(1200, 24, 160, 40),
                        2.0,
                        170,
                        "0123456789, "
                ))
        );
        YoloConfig yoloConfig = new YoloConfig(
                yoloEnabled,
                "models/ui-detector.onnx",
                "models/ui-detector.labels.txt",
                640,
                640,
                0.25,
                0.45,
                25,
                new RegionConfig(560, 260, 780, 500),
                List.of("npc")
        );
        return new VisionConfig(
                "Torchlight: Infinite",
                "img/sggd/largeMap_2.bmp",
                "img/arrow_template2.bmp",
                new RegionConfig(0, 100, 200, 200),
                new PointConfig(500, 260),
                100,
                200.0,
                80,
                10.0,
                ocrConfig,
                yoloConfig,
                MapPreprocessConfig.defaults(),
                MapClosureConfig.defaults()
        );
    }
}
