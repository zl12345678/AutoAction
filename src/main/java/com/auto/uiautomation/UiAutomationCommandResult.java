package com.auto.uiautomation;

import java.util.List;
import java.util.Objects;

public record UiAutomationCommandResult(
        boolean success,
        String message,
        List<UiAutomationElement> elements
) {
    public UiAutomationCommandResult {
        message = message == null ? "" : message;
        Objects.requireNonNull(elements, "elements");
        elements = List.copyOf(elements);
    }
}
