package com.auto.config;

public record InputSettings(ClickBackend clickBackend, String interceptionHome) {
    public InputSettings(ClickBackend clickBackend) {
        this(clickBackend, "");
    }

    public static InputSettings defaults() {
        return new InputSettings(ClickBackend.WIN32, "");
    }
}
