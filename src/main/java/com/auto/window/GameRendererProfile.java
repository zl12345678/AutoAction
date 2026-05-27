package com.auto.window;

enum GameRendererProfile {
    UNREAL_SINGLE_HWND,
    UNITY_OR_CHILD,
    GENERIC;

    static GameRendererProfile fromClassName(String className) {
        if (className == null || className.isEmpty()) {
            return GENERIC;
        }
        if ("UnrealWindow".equalsIgnoreCase(className)) {
            return UNREAL_SINGLE_HWND;
        }
        if (Win32ClickTargetResolver.isUnityClass(className)) {
            return UNITY_OR_CHILD;
        }
        return GENERIC;
    }
}
