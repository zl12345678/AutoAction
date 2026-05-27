package com.auto.uiautomation;

public record UiAutomationRuleExecution(
        String ruleName,
        boolean success,
        String message
) {
}
