package com.auto.vision;

import com.auto.opencv.utils.ImageProcessor;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;

/**
 * Loads a hand-edited pathfinding map (e.g. from Paint.NET) back into the workbench.
 */
public final class PathfindingMapImporter {
    private PathfindingMapImporter() {
    }

    /**
     * Converts to single-channel and snaps to binary (black walkable, white obstacle).
     */
    public static BufferedImage normalizeEditedMap(BufferedImage source) {
        if (source == null) {
            throw new IllegalArgumentException("缺少寻路地图文件");
        }
        OpenCvLoader.load();
        Mat mat = ImageProcessor.bufferedImageToMat(source);
        Mat gray = new Mat();
        if (mat.channels() == 1) {
            gray = mat.clone();
        } else {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        }
        Mat binary = new Mat();
        Imgproc.threshold(gray, binary, 127, 255, Imgproc.THRESH_BINARY);
        return ImageProcessor.matToBufferedImage(binary);
    }
}
