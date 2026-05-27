package com.auto.vision;

import com.auto.opencv.utils.ImageProcessor;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class NavigationDebugArtifacts {
    private NavigationDebugArtifacts() {
    }

    public static NavigationDebugArtifact mat(String id, String label, Mat mat) {
        if (mat == null || mat.empty()) {
            return new NavigationDebugArtifact(id, label, null);
        }
        return new NavigationDebugArtifact(id, label, ImageProcessor.matToBufferedImage(toDisplay(mat)));
    }

    public static NavigationDebugArtifact image(String id, String label, BufferedImage image) {
        return new NavigationDebugArtifact(id, label, image == null ? null : NavigationDebugArtifactWriter.copyImage(image));
    }

    public static List<NavigationDebugArtifact> nonEmpty(List<NavigationDebugArtifact> artifacts) {
        List<NavigationDebugArtifact> filtered = new ArrayList<>();
        for (NavigationDebugArtifact artifact : artifacts) {
            if (artifact.image() != null) {
                filtered.add(artifact);
            }
        }
        return filtered;
    }

    private static Mat toDisplay(Mat mat) {
        if (mat.channels() == 1) {
            Mat bgr = new Mat();
            Imgproc.cvtColor(mat, bgr, Imgproc.COLOR_GRAY2BGR);
            return bgr;
        }
        return mat;
    }
}
