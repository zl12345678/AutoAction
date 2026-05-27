package com.auto.window;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Win32ClickTargetResolverTest {
    @Test
    public void unityClassNamesAreDetected() {
        assertTrue(Win32ClickTargetResolver.isUnityClass("UnityWndClass"));
        assertTrue(Win32ClickTargetResolver.isUnityClass("HwndWrapper[Something]"));
        assertFalse(Win32ClickTargetResolver.isUnityClass("Chrome_WidgetWin_1"));
    }
}
