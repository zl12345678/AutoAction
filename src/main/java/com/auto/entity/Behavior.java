package com.auto.entity;

/**
 * Behavior类用于封装具体的鼠标和键盘行为。
 */
public class Behavior {
    // 行为类型
    private Type type;
    // x坐标
    private int x;
    // y坐标
    private int y;
    // 键值
    private int button;
    // 键值
    private int key;
    // 延迟时间（毫秒）
    private int delay;
    // 是否为绝对坐标
    private boolean isAbsolute;
    // 构造函数
    public Behavior(Type type, int x, int y, int button, int key, int delay, boolean isAbsolute) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.button = button;
        this.key = key;
        this.delay = delay;
        this.isAbsolute = isAbsolute;
    }

    // Getter 和 Setter 方法
    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getButton() {
        return button;
    }

    public void setButton(int button) {
        this.button = button;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }
    public boolean isAbsolute() {
        return isAbsolute;
    }

    public void setAbsolute(boolean absolute) {
        isAbsolute = absolute;
    }
    /**
     * Type枚举定义了Action类支持的事件类型。
     * 包括鼠标点击、按下、释放、移动，
     * 以及键盘按下、释放和点击。
     */
    public enum Type {
        MOUSE_CLICK, MOUSE_PRESS, MOUSE_RELEASE, MOUSE_MOVE,
        KEY_PRESS, KEY_RELEASE, KEY_CLICK
    }
}