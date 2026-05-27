package com.auto.domain;

public enum MouseButton {
    NONE(0),
    LEFT(1),
    MIDDLE(2),
    RIGHT(3);

    private final int code;

    MouseButton(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MouseButton fromCode(int code) {
        for (MouseButton button : values()) {
            if (button.code == code) {
                return button;
            }
        }
        throw new IllegalArgumentException("Unsupported mouse button code: " + code);
    }
}
