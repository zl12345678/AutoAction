package com.auto.config;

public record UiAutomationActionConfig(
        String controlType,
        String name,
        String automationId,
        UiAutomationPattern pattern,
        String value
) {
    public UiAutomationActionConfig {
        if ((controlType == null || controlType.isBlank())
                && (name == null || name.isBlank())
                && (automationId == null || automationId.isBlank())) {
            throw new IllegalArgumentException("uiAutomation action requires at least one selector");
        }
        if (pattern == null) {
            throw new IllegalArgumentException("uiAutomation action pattern is required");
        }
        controlType = controlType == null ? "" : controlType;
        name = name == null ? "" : name;
        automationId = automationId == null ? "" : automationId;
        value = value == null ? "" : value;
        if (pattern == UiAutomationPattern.SET_VALUE && value.isBlank()) {
            throw new IllegalArgumentException("uiAutomation setValue action requires a value");
        }
    }
}
