package com.auto.config;

public enum ClickBackend {
    WIN32("win32"),
    INTERCEPTION("interception");

    private final String configValue;

    ClickBackend(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static ClickBackend fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return WIN32;
        }
        String normalized = value.trim().toLowerCase();
        for (ClickBackend backend : values()) {
            if (backend.configValue.equals(normalized)) {
                return backend;
            }
        }
        throw new IllegalArgumentException("Unsupported click backend: " + value);
    }
}
