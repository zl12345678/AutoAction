package com.auto.window;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Optional;

public final class AwtJnaWindowService implements WindowService {
    private final Robot robot;

    public AwtJnaWindowService() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new IllegalStateException("Failed to initialize Robot", e);
        }
    }

    @Override
    public int getActiveProcessId() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
        return processId.getValue();
    }

    @Override
    public Optional<WindowRef> findWindow(String title) {
        String normalizedRequestedTitle = normalizeTitle(title);
        if (normalizedRequestedTitle.isEmpty()) {
            return Optional.empty();
        }

        WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, title);
        Optional<WindowRef> exactMatch = toWindowRef(title, hwnd);
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        WindowSearchResult bestMatch = new WindowSearchResult();
        User32.INSTANCE.EnumWindows((windowHandle, pointer) -> {
            if (!User32.INSTANCE.IsWindowVisible(windowHandle)) {
                return true;
            }

            String currentTitle = readWindowTitle(windowHandle);
            int score = matchScore(normalizedRequestedTitle, normalizeTitle(currentTitle));
            if (score > bestMatch.score) {
                bestMatch.score = score;
                bestMatch.hwnd = windowHandle;
                bestMatch.title = currentTitle;
            }
            return true;
        }, Pointer.NULL);

        return toWindowRef(bestMatch.title, bestMatch.hwnd);
    }

    private Optional<WindowRef> toWindowRef(String title, WinDef.HWND hwnd) {
        if (hwnd == null) {
            return Optional.empty();
        }

        WinUser.RECT rect = new WinUser.RECT();
        if (!User32.INSTANCE.GetWindowRect(hwnd, rect)) {
            return Optional.empty();
        }

        Rectangle bounds = new Rectangle(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
        return Optional.of(new WindowRef(title, hwnd, bounds));
    }

    @Override
    public BufferedImage capture(Rectangle region) {
        return robot.createScreenCapture(region);
    }

    @Override
    public Rectangle getWindowBoundsOnScreen(WindowRef window) {
        Object handle = window.nativeHandle();
        if (handle instanceof WinDef.HWND hwnd) {
            WindowsDpi.enable();
            return PhysicalWindowBounds.readWindowBounds(hwnd);
        }
        return new Rectangle(window.bounds());
    }

    @Override
    public Rectangle getClientBoundsOnScreen(WindowRef window) {
        Object handle = window.nativeHandle();
        if (handle instanceof WinDef.HWND hwnd) {
            WindowsDpi.enable();
            return PhysicalWindowBounds.readClientBounds(hwnd);
        }
        return new Rectangle(window.bounds());
    }

    @Override
    public boolean isForeground(WindowRef window) {
        Object handle = window.nativeHandle();
        if (!(handle instanceof WinDef.HWND hwnd)) {
            return false;
        }
        WinDef.HWND foreground = User32.INSTANCE.GetForegroundWindow();
        return hwnd.equals(foreground);
    }

    @Override
    public boolean waitForForeground(WindowRef window, int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (isForeground(window)) {
                return true;
            }
            robot.delay(50);
        }
        return isForeground(window);
    }

    @Override
    public void focus(WindowRef window) {
        Object handle = window.nativeHandle();
        if (!(handle instanceof WinDef.HWND hwnd)) {
            return;
        }
        if (isForeground(window)) {
            return;
        }

        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyRelease(KeyEvent.VK_ALT);
        robot.delay(30);

        WinDef.HWND foreground = User32.INSTANCE.GetForegroundWindow();
        WinDef.DWORD foregroundThread = new WinDef.DWORD(
                User32.INSTANCE.GetWindowThreadProcessId(foreground, null)
        );
        WinDef.DWORD targetThread = new WinDef.DWORD(User32.INSTANCE.GetWindowThreadProcessId(hwnd, null));
        WinDef.DWORD currentThread = new WinDef.DWORD(Kernel32.INSTANCE.GetCurrentThreadId());

        boolean attachedToForeground = false;
        boolean attachedToTarget = false;
        try {
            if (foregroundThread.intValue() != 0 && !foregroundThread.equals(targetThread)) {
                attachedToForeground = User32.INSTANCE.AttachThreadInput(foregroundThread, targetThread, true);
            }
            if (!currentThread.equals(targetThread)) {
                attachedToTarget = User32.INSTANCE.AttachThreadInput(currentThread, targetThread, true);
            }
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
            User32.INSTANCE.SetForegroundWindow(hwnd);
            User32.INSTANCE.BringWindowToTop(hwnd);
            robot.delay(50);
        } finally {
            if (attachedToTarget) {
                User32.INSTANCE.AttachThreadInput(currentThread, targetThread, false);
            }
            if (attachedToForeground) {
                User32.INSTANCE.AttachThreadInput(foregroundThread, targetThread, false);
            }
        }
    }

    private static String readWindowTitle(WinDef.HWND hwnd) {
        int length = User32.INSTANCE.GetWindowTextLength(hwnd);
        if (length <= 0) {
            return "";
        }
        char[] titleBuffer = new char[length + 1];
        User32.INSTANCE.GetWindowText(hwnd, titleBuffer, titleBuffer.length);
        return Native.toString(titleBuffer);
    }

    private static String normalizeTitle(String title) {
        return title == null ? "" : title.trim().toLowerCase();
    }

    private static int matchScore(String requested, String actual) {
        if (requested.isEmpty() || actual.isEmpty()) {
            return -1;
        }
        if (actual.equals(requested)) {
            return 3;
        }
        if (actual.contains(requested)) {
            return 2;
        }
        if (requested.contains(actual)) {
            return 1;
        }
        return -1;
    }

    private static final class WindowSearchResult {
        private int score = -1;
        private String title = "";
        private WinDef.HWND hwnd;
    }
}
