package com.auto.opencv.process;

import org.opencv.core.Mat;

public record ArrowMatchDebug(
        ArrowMatchResult result,
        Mat colorMask,
        Mat colorIsolated,
        Mat binaryPreview
) {
}
