package com.auto.input;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;

import java.awt.AWTException;
import java.awt.Point;
import java.awt.Robot;

/**
 * Win32 mouse control using the same physical screen pixel space as JNA window bounds.
 */
public final class Win32MouseInput {
    private static final int MOUSEEVENTF_MOVE = 0x0001;
    private static final int MOUSEEVENTF_LEFTDOWN = 0x0002;
    private static final int MOUSEEVENTF_LEFTUP = 0x0004;
    private static final int MOUSEEVENTF_ABSOLUTE = 0x8000;
    private static final int MOUSEEVENTF_VIRTUALDESKTOP = 0x4000;
    private static final int MOUSEEVENTF_ABSOLUTE_MOVE =
            MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESKTOP;
    private static final int MAX_MOVE_ERROR_PX = 6;
    public static final int DEFAULT_MOVE_TOLERANCE_PX = 16;

    private Win32MouseInput() {
    }

    public static Point getCursorPos() {
        WinUser.POINT point = new WinUser.POINT();
        if (!User32.INSTANCE.GetCursorPos(point)) {
            return java.awt.MouseInfo.getPointerInfo().getLocation();
        }
        return new Point(point.x, point.y);
    }

    public static MouseMoveResult moveToScreen(Point screenPoint) {
        return moveToScreen((int) Math.round(screenPoint.getX()), (int) Math.round(screenPoint.getY()));
    }

    public static MouseMoveResult moveToScreen(int screenX, int screenY) {
        boolean setCursorPosOk = setCursorPos(screenX, screenY);
        boolean usedRobotFallback = false;
        Point actual = getCursorPos();
        double error = Math.hypot(actual.x - screenX, actual.y - screenY);

        if (!setCursorPosOk || error > MAX_MOVE_ERROR_PX) {
            usedRobotFallback = true;
            fallbackRobot().mouseMove(screenX, screenY);
            actual = getCursorPos();
            error = Math.hypot(actual.x - screenX, actual.y - screenY);
        }

        return new MouseMoveResult(
                screenX,
                screenY,
                actual.x,
                actual.y,
                setCursorPosOk,
                usedRobotFallback,
                error
        );
    }

    public static void logMoveFailure(MouseMoveResult result) {
        if (result == null || result.succeeded(DEFAULT_MOVE_TOLERANCE_PX)) {
            return;
        }
        System.out.println("Win32MouseInput: 鼠标未到达目标 — " + result.diagnostic());
    }

    public static void leftClick(int holdMs) {
        if (!send(mouseButtonInput(MOUSEEVENTF_LEFTDOWN))) {
            Robot robot = fallbackRobot();
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            sleep(Math.max(0, holdMs));
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            return;
        }
        sleep(Math.max(0, holdMs));
        if (!send(mouseButtonInput(MOUSEEVENTF_LEFTUP))) {
            fallbackRobot().mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        }
    }

    public static MouseMoveResult clickAtScreen(Point screenPoint, int holdMs, int repeatCount, int gapMs) {
        int x = (int) Math.round(screenPoint.getX());
        int y = (int) Math.round(screenPoint.getY());
        int repeats = Math.max(1, repeatCount);
        MouseMoveResult lastMove = null;
        for (int i = 0; i < repeats; i++) {
            lastMove = moveToScreen(x, y);
            if (!lastMove.succeeded(DEFAULT_MOVE_TOLERANCE_PX)) {
                logMoveFailure(lastMove);
            }
            sleep(30);
            leftClick(holdMs);
            if (i + 1 < repeats) {
                sleep(Math.max(0, gapMs));
            }
        }
        return lastMove == null ? moveToScreen(x, y) : lastMove;
    }

    /**
     * Clicks at screen pixels via SendInput absolute coordinates without relying on SetCursorPos.
     */
    public static void clickAtScreenSendInput(Point screenPoint, int holdMs, int repeatCount, int gapMs) {
        int x = (int) Math.round(screenPoint.getX());
        int y = (int) Math.round(screenPoint.getY());
        int repeats = Math.max(1, repeatCount);
        for (int i = 0; i < repeats; i++) {
            sendAbsoluteClick(x, y, holdMs);
            if (i + 1 < repeats) {
                sleep(Math.max(0, gapMs));
            }
        }
    }

    static int[] toNormalizedVirtualDesktop(int screenX, int screenY) {
        int left = User32.INSTANCE.GetSystemMetrics(WinUser.SM_XVIRTUALSCREEN);
        int top = User32.INSTANCE.GetSystemMetrics(WinUser.SM_YVIRTUALSCREEN);
        int width = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXVIRTUALSCREEN);
        int height = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYVIRTUALSCREEN);
        int maxX = Math.max(1, width - 1);
        int maxY = Math.max(1, height - 1);
        int normalizedX = (int) Math.round((screenX - left) * 65535.0 / maxX);
        int normalizedY = (int) Math.round((screenY - top) * 65535.0 / maxY);
        return new int[] {
                Math.max(0, Math.min(65535, normalizedX)),
                Math.max(0, Math.min(65535, normalizedY))
        };
    }

    private static void sendAbsoluteClick(int screenX, int screenY, int holdMs) {
        int downFlags = MOUSEEVENTF_LEFTDOWN | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESKTOP;
        int upFlags = MOUSEEVENTF_LEFTUP | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESKTOP;
        WinUser.INPUT move = mouseAbsoluteInput(screenX, screenY, MOUSEEVENTF_ABSOLUTE_MOVE);
        WinUser.INPUT down = mouseAbsoluteInput(screenX, screenY, downFlags);
        if (!sendBatch(move, down)) {
            throw new IllegalStateException("SendInput move/down failed at (" + screenX + ", " + screenY + ")");
        }
        sleep(Math.max(0, holdMs));
        if (!send(mouseAbsoluteInput(screenX, screenY, upFlags))) {
            throw new IllegalStateException("SendInput up failed at (" + screenX + ", " + screenY + ")");
        }
    }

    private static WinUser.INPUT mouseAbsoluteInput(int screenX, int screenY, int flags) {
        int[] normalized = toNormalizedVirtualDesktop(screenX, screenY);
        WinUser.INPUT input = new WinUser.INPUT();
        input.type = new com.sun.jna.platform.win32.WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
        input.input.setType("mi");
        input.input.mi.dx = new com.sun.jna.platform.win32.WinDef.LONG(normalized[0]);
        input.input.mi.dy = new com.sun.jna.platform.win32.WinDef.LONG(normalized[1]);
        input.input.mi.dwFlags = new com.sun.jna.platform.win32.WinDef.DWORD(flags);
        return input;
    }

    private static boolean sendBatch(WinUser.INPUT first, WinUser.INPUT second) {
        WinUser.INPUT[] batch = new WinUser.INPUT[] {first, second};
        com.sun.jna.platform.win32.WinDef.DWORD sent =
                User32.INSTANCE.SendInput(
                        new com.sun.jna.platform.win32.WinDef.DWORD(batch.length),
                        batch,
                        first.size()
                );
        return sent.intValue() == batch.length;
    }

    public static boolean trySetCursorPos(int screenX, int screenY) {
        return User32.INSTANCE.SetCursorPos(screenX, screenY);
    }

    private static boolean setCursorPos(int screenX, int screenY) {
        return trySetCursorPos(screenX, screenY);
    }

    private static WinUser.INPUT mouseButtonInput(int flag) {
        WinUser.INPUT input = new WinUser.INPUT();
        input.type = new com.sun.jna.platform.win32.WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
        input.input.setType("mi");
        input.input.mi.dwFlags = new com.sun.jna.platform.win32.WinDef.DWORD(flag);
        return input;
    }

    private static boolean send(WinUser.INPUT input) {
        WinUser.INPUT[] batch = new WinUser.INPUT[] {input};
        com.sun.jna.platform.win32.WinDef.DWORD sent =
                User32.INSTANCE.SendInput(new com.sun.jna.platform.win32.WinDef.DWORD(batch.length), batch, input.size());
        return sent.intValue() != 0;
    }

    private static Robot fallbackRobot() {
        try {
            return new Robot();
        } catch (AWTException exception) {
            throw new IllegalStateException("Unable to move or click mouse", exception);
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
