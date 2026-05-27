package com.auto.vision;

import com.auto.config.VisionConfig;
import com.auto.window.WindowRef;

import java.util.Optional;

public interface NavigationPipeline {
    Optional<NavigationStep> planNext(VisionConfig config, WindowRef window, int waypointIndex);
}
