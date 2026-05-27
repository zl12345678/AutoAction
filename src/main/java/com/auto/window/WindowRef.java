package com.auto.window;

import java.awt.Rectangle;
import java.util.Objects;

public final class WindowRef {
    private final String title;
    private final Object nativeHandle;
    private final Rectangle bounds;

    public WindowRef(String title, Object nativeHandle, Rectangle bounds) {
        this.title = title == null ? "" : title;
        this.nativeHandle = nativeHandle;
        this.bounds = new Rectangle(Objects.requireNonNull(bounds, "bounds"));
    }

    public String title() {
        return title;
    }

    public Object nativeHandle() {
        return nativeHandle;
    }

    public Rectangle bounds() {
        return new Rectangle(bounds);
    }
}
