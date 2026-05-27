package com.auto.window;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import java.awt.Point;

/**
 * Injects mouse clicks via Win32 messages when physical cursor control is blocked (e.g. raw input games).
 */
public final class Win32WindowMessageClick {
    private static final int WM_MOUSEMOVE = 0x0200;
    private static final int WM_LBUTTONDOWN = 0x0201;
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int MK_LBUTTON = 0x0001;

    private Win32WindowMessageClick() {
    }

    public static Point toClientPoint(WinDef.HWND hwnd, Point screenPoint) {
        if (hwnd == null || screenPoint == null) {
            return null;
        }
        WinUser.POINT point = new WinUser.POINT();
        point.x = (int) Math.round(screenPoint.getX());
        point.y = (int) Math.round(screenPoint.getY());
        if (!Win32Extras.User32Extra.INSTANCE.ScreenToClient(hwnd, point)) {
            return null;
        }
        return new Point(point.x, point.y);
    }

    static int packClientLParam(int clientX, int clientY) {
        return (clientY << 16) | (clientX & 0xFFFF);
    }

    public static void clickAtScreen(WinDef.HWND hwnd, Point screenPoint, int holdMs, int repeatCount, int gapMs) {
        Point clientPoint = toClientPoint(hwnd, screenPoint);
        if (clientPoint == null) {
            throw new IllegalStateException("ScreenToClient failed for screen point (" + screenPoint.x + ", " + screenPoint.y + ")");
        }
        clickAtClient(hwnd, clientPoint, holdMs, repeatCount, gapMs, false);
    }

    public static void clickAtClient(
            WinDef.HWND hwnd,
            Point clientPoint,
            int holdMs,
            int repeatCount,
            int gapMs,
            boolean synchronous
    ) {
        int lParam = packClientLParam(clientPoint.x, clientPoint.y);
        int repeats = Math.max(1, repeatCount);
        for (int i = 0; i < repeats; i++) {
            dispatch(hwnd, WM_MOUSEMOVE, 0, lParam, synchronous);
            dispatch(hwnd, WM_LBUTTONDOWN, MK_LBUTTON, lParam, synchronous);
            sleep(Math.max(0, holdMs));
            dispatch(hwnd, WM_LBUTTONUP, 0, lParam, synchronous);
            if (i + 1 < repeats) {
                sleep(Math.max(0, gapMs));
            }
        }
    }

    private static void dispatch(
            WinDef.HWND hwnd,
            int message,
            int wParam,
            int lParam,
            boolean synchronous
    ) {
        WinDef.WPARAM wp = new WinDef.WPARAM(wParam);
        WinDef.LPARAM lp = new WinDef.LPARAM(lParam);
        if (synchronous) {
            User32.INSTANCE.SendMessage(hwnd, message, wp, lp);
        } else {
            User32.INSTANCE.PostMessage(hwnd, message, wp, lp);
        }
    }

    private static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
