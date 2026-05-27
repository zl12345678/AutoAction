package com.auto.window;

import com.sun.jna.platform.win32.WinDef;

import java.awt.Point;

record Win32ClickTarget(
        WinDef.HWND hwnd,
        Point clientPoint,
        Point screenPoint,
        boolean usedChildWindow,
        String windowClass,
        GameRendererProfile rendererProfile
) {
}
