package com.auto.window;

import com.auto.config.ClickBackend;
import com.auto.input.InputController;
import com.auto.input.Win32MouseInput;
import com.auto.input.interception.InterceptionMouseInput;
import com.sun.jna.platform.win32.WinDef;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * Clicks inside a game window client area after ensuring it can receive input.
 */
public final class GameWindowClickHelper {
    private static final int FOCUS_SETTLE_MS = 150;
    private static final int PRIME_CLICK_DELAY_MS = 180;
    private static final int NAV_CLICK_HOLD_MS = 90;
    private static final int NAV_CLICK_REPEAT = 2;
    private static final int NAV_CLICK_GAP_MS = 140;
    private static final int CLIENT_TOP_ACTIVATION_Y = 24;

    private GameWindowClickHelper() {
    }

    public static GameWindowClickResult click(
            WindowService windowService,
            InputController input,
            WindowRef window,
            Point screenPoint
    ) {
        return click(windowService, input, window, screenPoint, ClickBackend.WIN32);
    }

    public static GameWindowClickResult click(
            WindowService windowService,
            InputController input,
            WindowRef window,
            Point screenPoint,
            ClickBackend clickBackend
    ) {
        WindowsDpi.enable();
        Rectangle windowBounds = windowService.getWindowBoundsOnScreen(window);
        Rectangle clientBounds = windowService.getClientBoundsOnScreen(window);
        WinDef.HWND hwnd = window.nativeHandle() instanceof WinDef.HWND handle ? handle : null;
        Point absolutePoint = ScreenCoordinates.toAbsoluteScreenPoint(hwnd, screenPoint, windowBounds);
        Point preparedPoint = WindowClickGeometry.prepareScreenClick(absolutePoint, clientBounds);

        Point mouseBefore = Win32MouseInput.getCursorPos();
        boolean foregroundBefore = windowService.isForeground(window);

        windowService.focus(window);
        input.delay(FOCUS_SETTLE_MS);
        boolean foregroundAfterFocus = windowService.waitForForeground(window, 500);

        boolean usedClientTopActivation = false;
        if (!foregroundAfterFocus) {
            usedClientTopActivation = activateViaClientTop(windowService, input, hwnd, clientBounds, clickBackend);
            foregroundAfterFocus = windowService.waitForForeground(window, 500);
        }

        boolean usedPrimingClick = !foregroundBefore || !foregroundAfterFocus;
        int clickCount = 0;
        ClickDeliveryMethod deliveryMethod = ClickDeliveryMethod.PHYSICAL;

        if (usedPrimingClick) {
            ClickBatchResult priming = clickWithFallback(
                    hwnd,
                    preparedPoint,
                    NAV_CLICK_HOLD_MS,
                    1,
                    0,
                    clickBackend
            );
            deliveryMethod = mergeDeliveryMethod(deliveryMethod, priming.deliveryMethod());
            clickCount += priming.clickCount();
            input.delay(PRIME_CLICK_DELAY_MS);
            windowService.focus(window);
            input.delay(FOCUS_SETTLE_MS);
        }

        ClickBatchResult navigation = clickWithFallback(
                hwnd,
                preparedPoint,
                NAV_CLICK_HOLD_MS,
                NAV_CLICK_REPEAT,
                NAV_CLICK_GAP_MS,
                clickBackend
        );
        deliveryMethod = mergeDeliveryMethod(deliveryMethod, navigation.deliveryMethod());
        clickCount += navigation.clickCount();

        boolean foregroundAfterClicks = windowService.isForeground(window);
        Point mouseAfter = Win32MouseInput.getCursorPos();
        return new GameWindowClickResult(
                window.title(),
                new Point((int) Math.round(screenPoint.getX()), (int) Math.round(screenPoint.getY())),
                new Point(absolutePoint),
                preparedPoint,
                foregroundBefore,
                foregroundAfterFocus,
                foregroundAfterClicks,
                clickCount,
                usedClientTopActivation,
                usedPrimingClick,
                mouseBefore,
                mouseAfter,
                windowBounds,
                deliveryMethod
        );
    }

    private static ClickBatchResult clickWithFallback(
            WinDef.HWND hwnd,
            Point screenPoint,
            int holdMs,
            int repeatCount,
            int gapMs,
            ClickBackend clickBackend
    ) {
        int repeats = Math.max(1, repeatCount);
        if (clickBackend == ClickBackend.INTERCEPTION) {
            return clickWithInterception(screenPoint, holdMs, repeats, gapMs);
        }

        if (canUsePhysicalClick(screenPoint)) {
            Win32MouseInput.clickAtScreen(screenPoint, holdMs, repeats, gapMs);
            return new ClickBatchResult(ClickDeliveryMethod.PHYSICAL, repeats);
        }

        if (hwnd != null) {
            Win32GameClickInjector.click(hwnd, screenPoint, holdMs, repeats, gapMs);
            return new ClickBatchResult(ClickDeliveryMethod.LAYERED, repeats);
        }

        System.out.println(
                "GameWindowClick: SendInput 绝对坐标点击"
                        + " 屏幕(" + Math.round(screenPoint.getX()) + "," + Math.round(screenPoint.getY()) + ")"
                        + " ×" + repeats
        );
        Win32MouseInput.clickAtScreenSendInput(screenPoint, holdMs, repeats, gapMs);
        return new ClickBatchResult(ClickDeliveryMethod.SEND_INPUT, repeats);
    }

    /**
     * Games often block SetCursorPos; avoid Robot retries and noisy probes when the cursor cannot move.
     */
    private static boolean canUsePhysicalClick(Point screenPoint) {
        int x = (int) Math.round(screenPoint.getX());
        int y = (int) Math.round(screenPoint.getY());
        if (!Win32MouseInput.trySetCursorPos(x, y)) {
            return false;
        }
        Point actual = Win32MouseInput.getCursorPos();
        double error = Math.hypot(actual.x - x, actual.y - y);
        return error <= Win32MouseInput.DEFAULT_MOVE_TOLERANCE_PX;
    }

    private static ClickBatchResult clickWithInterception(
            Point screenPoint,
            int holdMs,
            int repeatCount,
            int gapMs
    ) {
        int repeats = Math.max(1, repeatCount);
        if (!InterceptionMouseInput.isAvailable()) {
            System.out.println(
                    "GameWindowClick: Interception 未就绪，回退软件注入。"
                            + " " + com.auto.input.interception.InterceptionBootstrap.lastDiagnostic()
                            + " 详见 tools/interception/README.md"
            );
            return clickWithFallback(null, screenPoint, holdMs, repeatCount, gapMs, ClickBackend.WIN32);
        }

        System.out.println(
                "GameWindowClick: Interception 驱动点击"
                        + " 屏幕(" + Math.round(screenPoint.getX()) + "," + Math.round(screenPoint.getY()) + ")"
                        + " ×" + repeats
        );
        InterceptionMouseInput.clickAtScreen(screenPoint, holdMs, repeats, gapMs);
        return new ClickBatchResult(ClickDeliveryMethod.INTERCEPTION, repeats);
    }

    private static ClickDeliveryMethod mergeDeliveryMethod(
            ClickDeliveryMethod current,
            ClickDeliveryMethod next
    ) {
        if (current == ClickDeliveryMethod.INTERCEPTION || next == ClickDeliveryMethod.INTERCEPTION) {
            return ClickDeliveryMethod.INTERCEPTION;
        }
        if (current == ClickDeliveryMethod.LAYERED || next == ClickDeliveryMethod.LAYERED) {
            return ClickDeliveryMethod.LAYERED;
        }
        if (current == ClickDeliveryMethod.SEND_INPUT || next == ClickDeliveryMethod.SEND_INPUT) {
            return ClickDeliveryMethod.SEND_INPUT;
        }
        return ClickDeliveryMethod.PHYSICAL;
    }

    private static boolean activateViaClientTop(
            WindowService windowService,
            InputController input,
            WinDef.HWND hwnd,
            Rectangle clientBounds,
            ClickBackend clickBackend
    ) {
        int activationX = clientBounds.x + Math.max(40, clientBounds.width / 2);
        int activationY = clientBounds.y + CLIENT_TOP_ACTIVATION_Y;
        Point activationPoint = new Point(activationX, activationY);
        clickWithFallback(hwnd, activationPoint, NAV_CLICK_HOLD_MS, 1, 0, clickBackend);
        input.delay(PRIME_CLICK_DELAY_MS);
        return true;
    }

    private record ClickBatchResult(ClickDeliveryMethod deliveryMethod, int clickCount) {
    }
}
