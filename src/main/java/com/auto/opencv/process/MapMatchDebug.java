package com.auto.opencv.process;

import org.opencv.core.Mat;

import java.util.List;

public record MapMatchDebug(
        MapMatchResult result,
        Mat patchRaw,
        Mat patchPrepared,
        Mat largeMapPrepared,
        Mat patchPreparedInverted,
        Mat templateHeatmap,
        List<String> attempts
) {
}
