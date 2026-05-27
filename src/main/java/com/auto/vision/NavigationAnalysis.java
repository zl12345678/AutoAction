package com.auto.vision;

import org.opencv.core.Point;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Optional;

public record NavigationAnalysis(
        String sourceName,
        BufferedImage sourceImage,
        BufferedImage miniMapImage,
        BufferedImage miniMapPreviewImage,
        BufferedImage mapPreviewImage,
        BufferedImage clickPreviewImage,
        Point arrowCenter,
        Point currentMapPoint,
        Point targetMapPoint,
        Point nextMapPoint,
        Point nextScreenPoint,
        int[][] path,
        boolean arrived,
        boolean success,
        String message,
        double localizationConfidence,
        LocalizationMethod localizationMethod,
        Point localizationRawPoint,
        boolean localizationOutlierRejected,
        String localizationDetail
) {
    public NavigationAnalysis {
        sourceName = sourceName == null ? "" : sourceName;
        path = path == null ? new int[0][2] : path;
        message = message == null ? "" : message;
        localizationDetail = localizationDetail == null ? "" : localizationDetail;
        if (localizationMethod == null) {
            localizationMethod = LocalizationMethod.NONE;
        }
    }

    public NavigationAnalysis(
            String sourceName,
            BufferedImage sourceImage,
            BufferedImage miniMapImage,
            BufferedImage miniMapPreviewImage,
            BufferedImage mapPreviewImage,
            BufferedImage clickPreviewImage,
            Point arrowCenter,
            Point currentMapPoint,
            Point targetMapPoint,
            Point nextMapPoint,
            Point nextScreenPoint,
            int[][] path,
            boolean arrived,
            boolean success,
            String message,
            double localizationConfidence,
            LocalizationMethod localizationMethod
    ) {
        this(
                sourceName,
                sourceImage,
                miniMapImage,
                miniMapPreviewImage,
                mapPreviewImage,
                clickPreviewImage,
                arrowCenter,
                currentMapPoint,
                targetMapPoint,
                nextMapPoint,
                nextScreenPoint,
                path,
                arrived,
                success,
                message,
                localizationConfidence,
                localizationMethod,
                null,
                false,
                ""
        );
    }

    public NavigationAnalysis(
            String sourceName,
            BufferedImage sourceImage,
            BufferedImage miniMapImage,
            BufferedImage miniMapPreviewImage,
            BufferedImage mapPreviewImage,
            BufferedImage clickPreviewImage,
            Point arrowCenter,
            Point currentMapPoint,
            Point targetMapPoint,
            Point nextMapPoint,
            Point nextScreenPoint,
            int[][] path,
            boolean arrived,
            boolean success,
            String message
    ) {
        this(
                sourceName,
                sourceImage,
                miniMapImage,
                miniMapPreviewImage,
                mapPreviewImage,
                clickPreviewImage,
                arrowCenter,
                currentMapPoint,
                targetMapPoint,
                nextMapPoint,
                nextScreenPoint,
                path,
                arrived,
                success,
                message,
                success ? 1.0 : 0.0,
                success ? LocalizationMethod.MAP_MATCH : LocalizationMethod.NONE,
                null,
                false,
                ""
        );
    }

    public Optional<NavigationStep> toNavigationStep() {
        if (!success || currentMapPoint == null) {
            return Optional.empty();
        }
        Point immediateTarget = arrived ? targetMapPoint : nextMapPoint;
        return Optional.of(new NavigationStep(
                currentMapPoint,
                immediateTarget,
                nextScreenPoint,
                path,
                arrived,
                localizationConfidence,
                localizationMethod
        ));
    }

    public String debugSummary() {
        return "source=" + sourceName
                + ", success=" + success
                + ", arrived=" + arrived
                + ", arrow=" + arrowCenter
                + ", current=" + currentMapPoint
                + ", target=" + targetMapPoint
                + ", nextMap=" + nextMapPoint
                + ", nextScreen=" + nextScreenPoint
                + ", confidence=" + localizationConfidence
                + ", method=" + localizationMethod
                + ", path=" + Arrays.deepToString(path)
                + ", message=" + message;
    }
}
