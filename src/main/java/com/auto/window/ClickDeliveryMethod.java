package com.auto.window;

public enum ClickDeliveryMethod {
    PHYSICAL("物理鼠标"),
    SEND_INPUT("SendInput 注入"),
    LAYERED("分层注入"),
    INTERCEPTION("Interception 驱动");

    private final String label;

    ClickDeliveryMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
