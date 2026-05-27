package com.auto.config;

import java.util.Objects;

public record AppConfig(
        SystemSettings system,
        VisionConfig vision,
        UiAutomationConfig uiAutomation,
        InputSettings input
) {
    public AppConfig(
            SystemSettings system,
            VisionConfig vision
    ) {
        this(system, vision, UiAutomationConfig.disabled(), InputSettings.defaults());
    }

    public AppConfig(
            SystemSettings system,
            VisionConfig vision,
            UiAutomationConfig uiAutomation
    ) {
        this(system, vision, uiAutomation, InputSettings.defaults());
    }

    public AppConfig {
        system = Objects.requireNonNull(system, "system");
        vision = Objects.requireNonNull(vision, "vision");
        uiAutomation = Objects.requireNonNull(uiAutomation, "uiAutomation");
        input = Objects.requireNonNull(input, "input");
    }
}
