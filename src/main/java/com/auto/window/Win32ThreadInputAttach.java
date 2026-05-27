package com.auto.window;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

final class Win32ThreadInputAttach {
    private Win32ThreadInputAttach() {
    }

    static void runAttached(WinDef.HWND hwnd, Runnable action) {
        if (hwnd == null) {
            action.run();
            return;
        }

        WinDef.DWORD targetThread = new WinDef.DWORD(User32.INSTANCE.GetWindowThreadProcessId(hwnd, null));
        WinDef.DWORD currentThread = new WinDef.DWORD(Kernel32.INSTANCE.GetCurrentThreadId());
        boolean attached = false;
        try {
            if (targetThread.intValue() != 0 && !currentThread.equals(targetThread)) {
                attached = User32.INSTANCE.AttachThreadInput(currentThread, targetThread, true);
            }
            action.run();
        } finally {
            if (attached) {
                User32.INSTANCE.AttachThreadInput(currentThread, targetThread, false);
            }
        }
    }
}
