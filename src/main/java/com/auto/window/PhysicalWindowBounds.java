package com.auto.window;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * Reads window bounds in physical screen pixels (matches GetCursorPos / SetCursorPos).
 */
final class PhysicalWindowBounds {
    private PhysicalWindowBounds() {
    }

    static Rectangle readWindowBounds(WinDef.HWND hwnd) {
        Rectangle physical = readExtendedFrameBounds(hwnd);
        if (physical != null) {
            return physical;
        }
        return readWindowRect(hwnd);
    }

    static Rectangle readClientBounds(WinDef.HWND hwnd) {
        WinUser.RECT clientRect = new WinUser.RECT();
        if (!User32.INSTANCE.GetClientRect(hwnd, clientRect)) {
            return readWindowBounds(hwnd);
        }

        int width = clientRect.right - clientRect.left;
        int height = clientRect.bottom - clientRect.top;
        if (width <= 0 || height <= 0) {
            return readWindowBounds(hwnd);
        }

        WinUser.POINT topLeft = new WinUser.POINT();
        topLeft.x = 0;
        topLeft.y = 0;
        if (!Win32Extras.User32Extra.INSTANCE.ClientToScreen(hwnd, topLeft)) {
            return readWindowBounds(hwnd);
        }
        return new Rectangle(topLeft.x, topLeft.y, width, height);
    }

    static Point mapWindowImagePointToScreen(WinDef.HWND hwnd, Point windowLocalPoint) {
        Rectangle frame = readWindowBounds(hwnd);
        if (frame.width > 0 && frame.height > 0) {
            return new Point(
                    frame.x + (int) Math.round(windowLocalPoint.getX()),
                    frame.y + (int) Math.round(windowLocalPoint.getY())
            );
        }

        WinUser.POINT mapped = new WinUser.POINT();
        mapped.x = (int) Math.round(windowLocalPoint.getX());
        mapped.y = (int) Math.round(windowLocalPoint.getY());
        if (Win32Extras.User32Extra.INSTANCE.MapWindowPoints(hwnd, null, mapped, 1)) {
            return new Point(mapped.x, mapped.y);
        }
        return new Point(windowLocalPoint);
    }

    private static Rectangle readExtendedFrameBounds(WinDef.HWND hwnd) {
        WinUser.RECT rect = new WinUser.RECT();
        int result = Win32Extras.DwmApiExtra.INSTANCE.DwmGetWindowAttribute(
                hwnd,
                Win32Extras.DWMWA_EXTENDED_FRAME_BOUNDS,
                rect.getPointer(),
                rect.size()
        );
        if (result != 0) {
            return null;
        }
        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new Rectangle(rect.left, rect.top, width, height);
    }

    private static Rectangle readWindowRect(WinDef.HWND hwnd) {
        WinUser.RECT rect = new WinUser.RECT();
        if (!User32.INSTANCE.GetWindowRect(hwnd, rect)) {
            return new Rectangle(0, 0, 0, 0);
        }
        return new Rectangle(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
    }
}
