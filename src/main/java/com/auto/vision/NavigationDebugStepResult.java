package com.auto.vision;

import java.util.Collections;
import java.util.List;

public record NavigationDebugStepResult(
        NavigationDebugStep step,
        boolean success,
        String message,
        PreviewTab previewTab,
        NavigationAnalysis analysis,
        List<NavigationDebugArtifact> artifacts,
        String artifactDir
) {
    public NavigationDebugStepResult {
        message = message == null ? "" : message;
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        artifactDir = artifactDir == null ? "" : artifactDir;
    }

    public static NavigationDebugStepResult failed(NavigationDebugStep step, String message) {
        return failed(step, message, List.of());
    }

    public static NavigationDebugStepResult failed(
            NavigationDebugStep step,
            String message,
            List<NavigationDebugArtifact> artifacts
    ) {
        return new NavigationDebugStepResult(step, false, message, null, null, artifacts, "");
    }
}
