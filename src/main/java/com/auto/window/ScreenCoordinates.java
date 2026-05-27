package com.auto.window;

import com.sun.jna.platform.win32.WinDef;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * Converts vision pipeline points (window-image local) to absolute screen pixels.
 */
public final class ScreenCoordinates {
    private ScreenCoordinates() {
    }

    public static Point toAbsoluteScreenPoint(Point point, Rectangle windowBoundsOnScreen) {
        if (point == null || windowBoundsOnScreen == null) {
            return point == null ? null : new Point(point);
        }
        if (isWindowLocalPoint(point, windowBoundsOnScreen)) {
            return new Point(
                    windowBoundsOnScreen.x + (int) Math.round(point.getX()),
                    windowBoundsOnScreen.y + (int) Math.round(point.getY())
            );
        }
        return new Point(point);
    }

    public static Point toAbsoluteScreenPoint(WinDef.HWND hwnd, Point point, Rectangle windowBoundsOnScreen) {
        if (point == null) {
            return null;
        }
        if (hwnd != null && isWindowLocalPoint(point, windowBoundsOnScreen)) {
            return PhysicalWindowBounds.mapWindowImagePointToScreen(hwnd, point);
        }
        return toAbsoluteScreenPoint(point, windowBoundsOnScreen);
    }

    static boolean isWindowLocalPoint(Point point, Rectangle windowBoundsOnScreen) {
        if (windowBoundsOnScreen == null || windowBoundsOnScreen.width <= 0 || windowBoundsOnScreen.height <= 0) {
            return false;
        }
        return point.getX() >= 0
                && point.getY() >= 0
                && point.getX() < windowBoundsOnScreen.width
                && point.getY() < windowBoundsOnScreen.height;
    }
}
