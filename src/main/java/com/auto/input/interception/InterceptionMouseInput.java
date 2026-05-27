package com.auto.input.interception;

import com.sun.jna.Pointer;

import java.awt.Point;

/**
 * Driver-level mouse input via the Interception library (works with Logitech and other USB mice).
 */
public final class InterceptionMouseInput {
    private static final Object LOCK = new Object();
    private static volatile Availability availability = Availability.UNKNOWN;

    private InterceptionMouseInput() {
    }

    public static boolean isAvailable() {
        return probeAvailability() == Availability.AVAILABLE;
    }

    public static void clickAtScreen(Point screenPoint, int holdMs, int repeatCount, int gapMs) {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "Interception 不可用。请安装驱动并将 interception.dll 放到 tools/interception/，参见 tools/interception/README.md"
            );
        }

        int x = (int) Math.round(screenPoint.getX());
        int y = (int) Math.round(screenPoint.getY());
        int repeats = Math.max(1, repeatCount);
        synchronized (LOCK) {
            Pointer context = InterceptionNative.get().interception_create_context();
            if (context == null) {
                throw new IllegalStateException("interception_create_context 失败");
            }
            try {
                int device = InterceptionNative.firstMouseDevice();
                for (int i = 0; i < repeats; i++) {
                    sendAbsoluteClick(context, device, x, y, holdMs);
                    if (i + 1 < repeats) {
                        sleep(Math.max(0, gapMs));
                    }
                }
            } finally {
                InterceptionNative.get().interception_destroy_context(context);
            }
        }
    }

    private static void sendAbsoluteClick(Pointer context, int device, int screenX, int screenY, int holdMs) {
        int[] normalized = InterceptionNative.toNormalizedVirtualDesktop(screenX, screenY);
        InterceptionNative.InterceptionStroke move = InterceptionNative.InterceptionStroke.moveAbsolute(
                normalized[0],
                normalized[1]
        );
        move.write();
        if (InterceptionNative.get().interception_send(context, device, move, 1) == 0) {
            throw new IllegalStateException("Interception 移动失败 (" + screenX + ", " + screenY + ")");
        }

        sleep(20);
        InterceptionNative.InterceptionStroke down =
                InterceptionNative.InterceptionStroke.button(InterceptionNative.INTERCEPTION_MOUSE_LEFT_BUTTON_DOWN);
        down.write();
        if (InterceptionNative.get().interception_send(context, device, down, 1) == 0) {
            throw new IllegalStateException("Interception 按下失败");
        }

        sleep(Math.max(0, holdMs));
        InterceptionNative.InterceptionStroke up =
                InterceptionNative.InterceptionStroke.button(InterceptionNative.INTERCEPTION_MOUSE_LEFT_BUTTON_UP);
        up.write();
        if (InterceptionNative.get().interception_send(context, device, up, 1) == 0) {
            throw new IllegalStateException("Interception 抬起失败");
        }
    }

    private static Availability probeAvailability() {
        if (availability != Availability.UNKNOWN) {
            return availability;
        }
        synchronized (LOCK) {
            if (availability != Availability.UNKNOWN) {
                return availability;
            }
            try {
                Pointer context = InterceptionNative.get().interception_create_context();
                if (context == null) {
                    InterceptionBootstrap.markDriverMissing();
                    availability = Availability.MISSING;
                } else {
                    InterceptionNative.get().interception_destroy_context(context);
                    availability = Availability.AVAILABLE;
                }
            } catch (UnsatisfiedLinkError | RuntimeException error) {
                InterceptionBootstrap.markLoadFailure(error);
                availability = Availability.MISSING;
            }
            return availability;
        }
    }

    private static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private enum Availability {
        UNKNOWN,
        AVAILABLE,
        MISSING
    }
}
