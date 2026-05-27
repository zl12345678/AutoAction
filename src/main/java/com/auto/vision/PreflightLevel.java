package com.auto.vision;

public enum PreflightLevel {
    OK("通过"),
    WARN("警告"),
    FAIL("失败");

    private final String label;

    PreflightLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
