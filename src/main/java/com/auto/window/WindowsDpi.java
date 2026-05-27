package com.auto.window;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Ensures Win32 window rectangles and cursor coordinates use the same physical pixel space.
 */
public final class WindowsDpi {
    private static final int PROCESS_PER_MONITOR_DPI_AWARE = 2;

    private interface User32Dpi extends StdCallLibrary {
        User32Dpi INSTANCE = Native.load("user32", User32Dpi.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean SetProcessDPIAware();
    }

    private interface ShcoreDpi extends StdCallLibrary {
        ShcoreDpi INSTANCE = Native.load("shcore", ShcoreDpi.class, W32APIOptions.DEFAULT_OPTIONS);

        WinNT.HRESULT SetProcessDpiAwareness(int awareness);
    }

    private static volatile boolean enabled;

    private WindowsDpi() {
    }

    public static void enable() {
        if (enabled) {
            return;
        }
        enabled = true;
        try {
            ShcoreDpi.INSTANCE.SetProcessDpiAwareness(PROCESS_PER_MONITOR_DPI_AWARE);
        } catch (UnsatisfiedLinkError | RuntimeException ignored) {
            try {
                User32Dpi.INSTANCE.SetProcessDPIAware();
            } catch (UnsatisfiedLinkError | RuntimeException ignoredAgain) {
                // Best effort only.
            }
        }
    }
}
