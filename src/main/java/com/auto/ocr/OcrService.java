package com.auto.ocr;

import com.auto.config.OcrConfig;

import java.awt.image.BufferedImage;
import java.util.List;

public interface OcrService {
    List<OcrResult> recognize(OcrConfig config, BufferedImage windowImage, BufferedImage miniMapImage);
}
