package com.auto.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Objects;

public record OcrResult(
        String name,
        String text,
        double confidence,
        Rectangle bounds,
        BufferedImage preprocessedPreviewImage
) {
    public OcrResult {
        name = name == null ? "" : name;
        text = text == null ? "" : text;
        if (confidence < 0.0) {
            confidence = 0.0;
        }
        bounds = new Rectangle(Objects.requireNonNull(bounds, "bounds"));
    }
}
