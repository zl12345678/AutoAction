package com.auto.config;

import java.util.List;
import java.util.Objects;

public record UiAutomationConfig(
        boolean enabled,
        List<String> helperCommand,
        int timeoutMs,
        List<UiAutomationRuleConfig> rules
) {
    public static UiAutomationConfig disabled() {
        return new UiAutomationConfig(
                false,
                List.of("powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/windows-ui-automation-helper.ps1"),
                10_000,
                List.of()
        );
    }

    public UiAutomationConfig {
        Objects.requireNonNull(helperCommand, "helperCommand");
        helperCommand = List.copyOf(helperCommand);
        if (helperCommand.isEmpty()) {
            throw new IllegalArgumentException("uiAutomation helperCommand is required");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("uiAutomation timeoutMs must be positive");
        }
        Objects.requireNonNull(rules, "rules");
        rules = List.copyOf(rules);
    }
}
