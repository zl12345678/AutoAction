package com.auto.vision;

import com.auto.config.VisionConfig;
import com.auto.window.WindowRef;

import java.util.Optional;

public interface NavigationTickClient {
    Optional<NavigationAnalysis> analyzeNext(VisionConfig config, WindowRef window, int waypointIndex);
}
