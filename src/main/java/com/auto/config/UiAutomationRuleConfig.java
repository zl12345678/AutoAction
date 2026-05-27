package com.auto.config;

import java.util.List;
import java.util.Objects;

public record UiAutomationRuleConfig(
        String name,
        String windowTitleContains,
        List<UiAutomationActionConfig> actions
) {
    public UiAutomationRuleConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("uiAutomation rule name is required");
        }
        if (windowTitleContains == null || windowTitleContains.isBlank()) {
            throw new IllegalArgumentException("uiAutomation rule windowTitleContains is required");
        }
        Objects.requireNonNull(actions, "actions");
        actions = List.copyOf(actions);
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("uiAutomation rule requires at least one action");
        }
    }
}
