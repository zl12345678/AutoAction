package com.auto.window;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GameRendererProfileTest {
    @Test
    public void detectsUnrealWindow() {
        assertEquals(GameRendererProfile.UNREAL_SINGLE_HWND, GameRendererProfile.fromClassName("UnrealWindow"));
    }

    @Test
    public void detectsUnityWindow() {
        assertEquals(GameRendererProfile.UNITY_OR_CHILD, GameRendererProfile.fromClassName("UnityWndClass"));
    }
}
