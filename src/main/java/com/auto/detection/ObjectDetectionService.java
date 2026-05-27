package com.auto.detection;

import com.auto.config.YoloConfig;

import java.awt.image.BufferedImage;
import java.util.List;

public interface ObjectDetectionService {
    List<DetectedObject> detect(YoloConfig config, BufferedImage sourceImage);
}
