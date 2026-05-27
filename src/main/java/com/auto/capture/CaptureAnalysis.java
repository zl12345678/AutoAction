package com.auto.capture;

import com.auto.detection.DetectedObject;
import com.auto.ocr.OcrResult;
import com.auto.vision.NavigationAnalysis;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CaptureAnalysis(
        BufferedImage sourceImage,
        NavigationAnalysis navigationAnalysis,
        List<OcrResult> ocrResults,
        List<DetectedObject> detections,
        BufferedImage combinedPreviewImage,
        List<String> diagnostics,
        String sourceName,
        Instant capturedAt
) {
    public CaptureAnalysis {
        navigationAnalysis = Objects.requireNonNull(navigationAnalysis, "navigationAnalysis");
        Objects.requireNonNull(ocrResults, "ocrResults");
        ocrResults = List.copyOf(ocrResults);
        Objects.requireNonNull(detections, "detections");
        detections = List.copyOf(detections);
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
        sourceName = sourceName == null ? "" : sourceName;
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
    }
}
