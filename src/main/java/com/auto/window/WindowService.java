package com.auto.window;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;

public interface WindowService {
    int getActiveProcessId();

    Optional<WindowRef> findWindow(String title);

    BufferedImage capture(Rectangle region);

    Rectangle getClientBoundsOnScreen(WindowRef window);

    Rectangle getWindowBoundsOnScreen(WindowRef window);

    boolean isForeground(WindowRef window);

    void focus(WindowRef window);

    default boolean waitForForeground(WindowRef window, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (isForeground(window)) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return isForeground(window);
    }
}
