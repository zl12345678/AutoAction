package com.auto.window;

import com.auto.input.Win32MouseInput;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delivers clicks to games that block cursor movement and ignore top-level synthetic input.
 */
public final class Win32GameClickInjector {
    private static final AtomicBoolean UNREAL_WARNING_LOGGED = new AtomicBoolean();

    private Win32GameClickInjector() {
    }

    public static void click(
            WinDef.HWND root,
            Point screenPoint,
            int holdMs,
            int repeatCount,
            int gapMs
    ) {
        Win32ClickTarget target = Win32ClickTargetResolver.resolve(root, screenPoint);
        logTarget(target);
        logUnrealHintOnce(target.rendererProfile());

        int repeats = Math.max(1, repeatCount);
        for (int i = 0; i < repeats; i++) {
            deliverOnce(root, target, holdMs);
            if (i + 1 < repeats) {
                sleep(Math.max(0, gapMs));
            }
        }
    }

    private static void logTarget(Win32ClickTarget target) {
        String layer = switch (target.rendererProfile()) {
            case UNREAL_SINGLE_HWND -> " [Unreal单窗]";
            case UNITY_OR_CHILD -> target.usedChildWindow() ? " [子窗口]" : " [顶层/单窗]";
            case GENERIC -> target.usedChildWindow() ? " [子窗口]" : " [顶层/单窗]";
        };
        System.out.println(
                "GameWindowClick: 分层注入"
                        + " 屏幕(" + target.screenPoint().x + "," + target.screenPoint().y + ")"
                        + " 目标HWND=0x" + Long.toHexString(Pointer.nativeValue(target.hwnd().getPointer()))
                        + " 类名=" + (target.windowClass().isEmpty() ? "?" : target.windowClass())
                        + " 客户区(" + target.clientPoint().x + "," + target.clientPoint().y + ")"
                        + layer
        );
    }

    private static void logUnrealHintOnce(GameRendererProfile profile) {
        if (profile != GameRendererProfile.UNREAL_SINGLE_HWND) {
            return;
        }
        if (!UNREAL_WARNING_LOGGED.compareAndSet(false, true)) {
            return;
        }
        System.out.println(
                "GameWindowClick: UnrealWindow 单 HWND（无子窗口）。"
                        + " UE 游戏常用 Raw Input，SendMessage/SetCursorPos 通常无效；"
                        + " 当前仅尝试 AttachThread + SendInput。"
                        + " 若仍无反应：请管理员运行本程序与游戏，或改用硬件鼠标盒/手动点击。"
        );
    }

    private static void deliverOnce(WinDef.HWND root, Win32ClickTarget primary, int holdMs) {
        if (primary.rendererProfile() == GameRendererProfile.UNREAL_SINGLE_HWND) {
            deliverUnreal(primary, holdMs);
            return;
        }

        deliverLayered(root, primary, holdMs);
    }

    private static void deliverUnreal(Win32ClickTarget target, int holdMs) {
        Win32ThreadInputAttach.runAttached(target.hwnd(), () ->
                Win32MouseInput.clickAtScreenSendInput(target.screenPoint(), holdMs, 1, 0)
        );
    }

    private static void deliverLayered(WinDef.HWND root, Win32ClickTarget primary, int holdMs) {
        deliverToTarget(primary, holdMs);
        if (!primary.usedChildWindow()) {
            logDescendantSummary(root, primary.screenPoint());
            Win32ClickTargetResolver.forEachDescendant(root, hwnd -> {
                if (hwnd.equals(primary.hwnd()) || hwnd.equals(root)) {
                    return;
                }
                if (!Win32ClickTargetResolver.isUnityClass(Win32ClickTargetResolver.readClassName(hwnd))) {
                    return;
                }
                if (!Win32ClickTargetResolver.containsScreenPoint(
                        hwnd,
                        primary.screenPoint().x,
                        primary.screenPoint().y
                )) {
                    return;
                }
                Point clientPoint = Win32WindowMessageClick.toClientPoint(hwnd, primary.screenPoint());
                if (clientPoint == null) {
                    return;
                }
                Win32ClickTarget extra = new Win32ClickTarget(
                        hwnd,
                        clientPoint,
                        primary.screenPoint(),
                        true,
                        Win32ClickTargetResolver.readClassName(hwnd),
                        GameRendererProfile.UNITY_OR_CHILD
                );
                System.out.println(
                        "GameWindowClick: Unity 子窗口补注"
                                + " HWND=0x" + Long.toHexString(Pointer.nativeValue(extra.hwnd().getPointer()))
                                + " 类名=" + extra.windowClass()
                                + " 客户区(" + extra.clientPoint().x + "," + extra.clientPoint().y + ")"
                );
                deliverToTarget(extra, holdMs);
            });
        }
    }

    private static void deliverToTarget(Win32ClickTarget target, int holdMs) {
        Win32ThreadInputAttach.runAttached(target.hwnd(), () -> {
            Win32WindowMessageClick.clickAtClient(target.hwnd(), target.clientPoint(), holdMs, 1, 0, true);
            Win32MouseInput.clickAtScreenSendInput(target.screenPoint(), holdMs, 1, 0);
        });
    }

    private static void logDescendantSummary(WinDef.HWND root, Point screenPoint) {
        int[] counts = new int[2];
        Win32ClickTargetResolver.forEachDescendant(root, hwnd -> {
            counts[0]++;
            if (Win32ClickTargetResolver.containsScreenPoint(hwnd, screenPoint.x, screenPoint.y)) {
                counts[1]++;
            }
        });
        if (counts[0] > 0) {
            System.out.println(
                    "GameWindowClick: 顶层单窗模式，后代窗口=" + counts[0]
                            + " 命中坐标=" + counts[1]
            );
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
