package com.auto.window;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

final class Win32Extras {
    interface User32Extra extends StdCallLibrary {
        User32Extra INSTANCE = Native.load("user32", User32Extra.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean ClientToScreen(WinDef.HWND hwnd, WinUser.POINT point);

        boolean ScreenToClient(WinDef.HWND hwnd, WinUser.POINT point);

        boolean MapWindowPoints(WinDef.HWND src, WinDef.HWND dst, WinUser.POINT points, int count);

        WinDef.HWND RealChildWindowFromPoint(WinDef.HWND parent, WinUser.POINT point);

        WinDef.HWND WindowFromPoint(WinUser.POINT point);

        boolean IsChild(WinDef.HWND parent, WinDef.HWND child);

        WinDef.HWND ChildWindowFromPointEx(WinDef.HWND parent, WinUser.POINT point, int flags);
    }

    static final int CWP_SKIPINVISIBLE = 0x0001;
    static final int CWP_SKIPTRANSPARENT = 0x0002;

    interface DwmApiExtra extends StdCallLibrary {
        DwmApiExtra INSTANCE = Native.load("dwmapi", DwmApiExtra.class, W32APIOptions.DEFAULT_OPTIONS);

        int DwmGetWindowAttribute(WinDef.HWND hwnd, int attribute, Pointer attributeValue, int attributeSize);
    }

    static final int DWMWA_EXTENDED_FRAME_BOUNDS = 9;

    private Win32Extras() {
    }
}
