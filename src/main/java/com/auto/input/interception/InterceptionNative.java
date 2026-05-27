package com.auto.input.interception;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;

import java.util.List;

interface InterceptionNative extends Library {
    int INTERCEPTION_MAX_KEYBOARD = 10;
    int INTERCEPTION_MOUSE_MOVE_ABSOLUTE = 0x001;
    int INTERCEPTION_MOUSE_VIRTUAL_DESKTOP = 0x002;
    int INTERCEPTION_MOUSE_LEFT_BUTTON_DOWN = 0x001;
    int INTERCEPTION_MOUSE_LEFT_BUTTON_UP = 0x002;

    final class Holder {
        private static volatile InterceptionNative instance;
        private static volatile Throwable loadError;

        private Holder() {
        }

        static InterceptionNative instance() {
            if (instance != null) {
                return instance;
            }
            if (loadError != null) {
                throw new IllegalStateException(loadError);
            }
            synchronized (Holder.class) {
                if (instance != null) {
                    return instance;
                }
                if (loadError != null) {
                    throw new IllegalStateException(loadError);
                }
                try {
                    var dll = InterceptionBootstrap.resolveDll();
                    if (dll == null) {
                        loadError = new UnsatisfiedLinkError(InterceptionBootstrap.lastDiagnostic());
                        throw new IllegalStateException(loadError);
                    }
                    instance = Native.load(dll.toString(), InterceptionNative.class);
                    return instance;
                } catch (UnsatisfiedLinkError | RuntimeException error) {
                    InterceptionBootstrap.markLoadFailure(error);
                    loadError = error;
                    throw error;
                }
            }
        }
    }

    static InterceptionNative get() {
        return Holder.instance();
    }

    Pointer interception_create_context();

    void interception_destroy_context(Pointer context);

    int interception_is_mouse(int device);

    int interception_is_invalid(int device);

    int interception_send(Pointer context, int device, InterceptionStroke stroke, int strokeCount);

    class InterceptionStroke extends Structure {
        public short state;
        public short flags;
        public short rolling;
        public int x;
        public int y;
        public int information;

        public InterceptionStroke() {
        }

        public static InterceptionStroke moveAbsolute(int normalizedX, int normalizedY) {
            InterceptionStroke stroke = new InterceptionStroke();
            stroke.state = 0;
            stroke.flags = (short) (INTERCEPTION_MOUSE_MOVE_ABSOLUTE | INTERCEPTION_MOUSE_VIRTUAL_DESKTOP);
            stroke.rolling = 0;
            stroke.x = normalizedX;
            stroke.y = normalizedY;
            stroke.information = 0;
            return stroke;
        }

        public static InterceptionStroke button(int buttonState) {
            InterceptionStroke stroke = new InterceptionStroke();
            stroke.state = (short) buttonState;
            stroke.flags = 0;
            stroke.rolling = 0;
            stroke.x = 0;
            stroke.y = 0;
            stroke.information = 0;
            return stroke;
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("state", "flags", "rolling", "x", "y", "information");
        }
    }

    static int[] toNormalizedVirtualDesktop(int screenX, int screenY) {
        int left = User32.INSTANCE.GetSystemMetrics(WinUser.SM_XVIRTUALSCREEN);
        int top = User32.INSTANCE.GetSystemMetrics(WinUser.SM_YVIRTUALSCREEN);
        int width = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXVIRTUALSCREEN);
        int height = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYVIRTUALSCREEN);
        int maxX = Math.max(1, width - 1);
        int maxY = Math.max(1, height - 1);
        int normalizedX = (int) Math.round((screenX - left) * 65535.0 / maxX);
        int normalizedY = (int) Math.round((screenY - top) * 65535.0 / maxY);
        return new int[] {
                Math.max(0, Math.min(65535, normalizedX)),
                Math.max(0, Math.min(65535, normalizedY))
        };
    }

    static int firstMouseDevice() {
        InterceptionNative api = get();
        for (int index = 0; index < 10; index++) {
            int device = INTERCEPTION_MAX_KEYBOARD + index + 1;
            if (api.interception_is_invalid(device) == 0 && api.interception_is_mouse(device) != 0) {
                return device;
            }
        }
        return INTERCEPTION_MAX_KEYBOARD + 1;
    }
}
