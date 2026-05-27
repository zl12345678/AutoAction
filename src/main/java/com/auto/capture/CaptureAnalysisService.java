package com.auto.capture;

import com.auto.config.VisionConfig;
import com.auto.detection.DetectedObject;
import com.auto.detection.ObjectDetectionService;
import com.auto.opencv.utils.ImageProcessor;
import com.auto.ocr.OcrResult;
import com.auto.ocr.OcrService;
import com.auto.vision.NavigationAnalysis;
import com.auto.vision.OpenCvNavigationAnalyzer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CaptureAnalysisService {
    private final OpenCvNavigationAnalyzer navigationAnalyzer;
    private final OcrService ocrService;
    private final ObjectDetectionService objectDetectionService;

    public CaptureAnalysisService() {
        this(new OpenCvNavigationAnalyzer(), new com.auto.ocr.TesseractOcrService(), new com.auto.detection.OnnxYoloDetectionService());
    }

    public CaptureAnalysisService(
            OpenCvNavigationAnalyzer navigationAnalyzer,
            OcrService ocrService,
            ObjectDetectionService objectDetectionService
    ) {
        this.navigationAnalyzer = navigationAnalyzer;
        this.ocrService = ocrService;
        this.objectDetectionService = objectDetectionService;
    }

    public CaptureAnalysis analyzeWindowCapture(
            VisionConfig config,
            BufferedImage windowCapture,
            Rectangle windowBounds,
            String sourceName
    ) {
        NavigationAnalysis navigationAnalysis = navigationAnalyzer.analyzeWindowCapture(config, windowCapture, windowBounds, sourceName);
        return buildCaptureAnalysis(config, navigationAnalysis, windowCapture, navigationAnalysis.miniMapImage(), sourceName);
    }

    public CaptureAnalysis analyzeMiniMap(VisionConfig config, BufferedImage miniMapImage, String sourceName) {
        NavigationAnalysis navigationAnalysis = navigationAnalyzer.analyzeMiniMap(config, miniMapImage, sourceName);
        return buildCaptureAnalysis(config, navigationAnalysis, null, miniMapImage, sourceName);
    }

    public CaptureAnalysis analyzeSample(VisionConfig config) {
        NavigationAnalysis navigationAnalysis = navigationAnalyzer.analyzeSample(config);
        return buildCaptureAnalysis(
                config,
                navigationAnalysis,
                navigationAnalysis.sourceImage(),
                navigationAnalysis.miniMapImage(),
                navigationAnalysis.sourceName()
        );
    }

    private CaptureAnalysis buildCaptureAnalysis(
            VisionConfig config,
            NavigationAnalysis navigationAnalysis,
            BufferedImage sourceImage,
            BufferedImage miniMapImage,
            String sourceName
    ) {
        List<String> diagnostics = new ArrayList<>();
        List<OcrResult> ocrResults = runOcr(config, sourceImage, miniMapImage, diagnostics);
        List<DetectedObject> detections = runDetection(config, sourceImage != null ? sourceImage : miniMapImage, diagnostics);
        BufferedImage combinedPreview = buildCombinedPreview(sourceImage != null ? sourceImage : miniMapImage, ocrResults, detections);
        return new CaptureAnalysis(
                sourceImage,
                navigationAnalysis,
                ocrResults,
                detections,
                combinedPreview,
                diagnostics,
                sourceName,
                Instant.now()
        );
    }

    private List<OcrResult> runOcr(
            VisionConfig config,
            BufferedImage sourceImage,
            BufferedImage miniMapImage,
            List<String> diagnostics
    ) {
        if (!config.ocr().enabled()) {
            return List.of();
        }
        if (config.ocr().regions().isEmpty()) {
            diagnostics.add("OCR 已启用，但没有配置任何识别区域。");
            return List.of();
        }
        try {
            List<OcrResult> results = ocrService.recognize(config.ocr(), sourceImage, miniMapImage);
            long nonBlank = results.stream().filter(result -> !result.text().isBlank()).count();
            diagnostics.add("OCR 已运行，区域 " + results.size() + " 个，非空结果 " + nonBlank + " 个。");
            return results;
        } catch (RuntimeException e) {
            diagnostics.add("OCR 运行失败: " + e.getMessage());
            return List.of();
        }
    }

    private List<DetectedObject> runDetection(
            VisionConfig config,
            BufferedImage sourceImage,
            List<String> diagnostics
    ) {
        if (!config.yolo().enabled()) {
            return List.of();
        }
        if (sourceImage == null) {
            diagnostics.add("YOLO 已启用，但当前没有可用于检测的图像。");
            return List.of();
        }
        try {
            List<DetectedObject> detections = objectDetectionService.detect(config.yolo(), sourceImage);
            diagnostics.add("YOLO 已运行，检测到 " + detections.size() + " 个对象。");
            return detections;
        } catch (RuntimeException e) {
            diagnostics.add("YOLO 运行失败: " + e.getMessage());
            return List.of();
        }
    }

    private static BufferedImage buildCombinedPreview(
            BufferedImage sourceImage,
            List<OcrResult> ocrResults,
            List<DetectedObject> detections
    ) {
        if (sourceImage == null) {
            return null;
        }
        BufferedImage preview = ImageProcessor.copyBufferedImage(sourceImage);
        Graphics2D graphics = preview.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setStroke(new BasicStroke(3f));

        graphics.setColor(new Color(0, 220, 120));
        for (OcrResult result : ocrResults) {
            Rectangle bounds = result.bounds();
            graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            graphics.drawString(result.name() + ": " + result.text(), bounds.x, Math.max(12, bounds.y - 6));
        }

        graphics.setColor(new Color(255, 180, 40));
        for (DetectedObject detection : detections) {
            Rectangle bounds = detection.bounds();
            graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            graphics.drawString(
                    detection.label() + String.format(" %.2f", detection.score()),
                    bounds.x,
                    Math.max(12, bounds.y - 6)
            );
        }
        graphics.dispose();
        return preview;
    }
}
