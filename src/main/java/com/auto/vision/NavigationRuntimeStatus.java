package com.auto.vision;

import org.opencv.core.Point;

import java.awt.image.BufferedImage;

public record NavigationRuntimeStatus(
        NavigationControllerState state,
        NavigationAction lastAction,
        String message,
        int waypointIndex,
        double localizationConfidence,
        LocalizationMethod localizationMethod,
        BufferedImage liveMapPreviewImage,
        Point localizationRawPoint,
        Point localizationAcceptedPoint,
        boolean localizationOutlierRejected,
        String localizationDetail
) {
    public NavigationRuntimeStatus(
            NavigationControllerState state,
            NavigationAction lastAction,
            String message,
            int waypointIndex,
            double localizationConfidence,
            LocalizationMethod localizationMethod
    ) {
        this(
                state,
                lastAction,
                message,
                waypointIndex,
                localizationConfidence,
                localizationMethod,
                null,
                null,
                null,
                false,
                ""
        );
    }

    public NavigationRuntimeStatus {
        localizationDetail = localizationDetail == null ? "" : localizationDetail;
        if (localizationMethod == null) {
            localizationMethod = LocalizationMethod.NONE;
        }
    }
}
