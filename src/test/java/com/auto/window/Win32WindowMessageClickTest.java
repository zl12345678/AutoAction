package com.auto.window;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Win32WindowMessageClickTest {
    @Test
    public void packClientLParamEncodesCoordinates() {
        assertEquals(0x00030002, Win32WindowMessageClick.packClientLParam(2, 3));
        assertEquals(0x02580064, Win32WindowMessageClick.packClientLParam(100, 600));
    }
}
