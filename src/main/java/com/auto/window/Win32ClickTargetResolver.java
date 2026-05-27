package com.auto.window;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.function.Consumer;

final class Win32ClickTargetResolver {
    private static final int CLASS_NAME_MAX = 256;

    private Win32ClickTargetResolver() {
    }

    static Win32ClickTarget resolve(WinDef.HWND root, Point screenPoint) {
        if (root == null || screenPoint == null) {
            throw new IllegalArgumentException("root hwnd and screen point are required");
        }

        int screenX = (int) Math.round(screenPoint.getX());
        int screenY = (int) Math.round(screenPoint.getY());
        Point rootClient = Win32WindowMessageClick.toClientPoint(root, screenPoint);

        WinDef.HWND target = root;
        if (rootClient != null) {
            target = pickBest(
                    root,
                    screenX,
                    screenY,
                    rootClient,
                    Win32Extras.User32Extra.INSTANCE.ChildWindowFromPointEx(
                            root,
                            toPoint(rootClient),
                            Win32Extras.CWP_SKIPINVISIBLE | Win32Extras.CWP_SKIPTRANSPARENT
                    ),
                    Win32Extras.User32Extra.INSTANCE.RealChildWindowFromPoint(root, toPoint(rootClient)),
                    deepestChildUnderRoot(root, screenX, screenY),
                    findDescendantAtScreenPoint(root, screenX, screenY)
            );
        }

        Point clientPoint = Win32WindowMessageClick.toClientPoint(target, screenPoint);
        if (clientPoint == null) {
            clientPoint = rootClient == null ? new Point(screenX, screenY) : new Point(rootClient);
        }
        boolean usedChild = !target.equals(root);
        String className = readClassName(target);
        return new Win32ClickTarget(
                target,
                clientPoint,
                new Point(screenX, screenY),
                usedChild,
                className,
                GameRendererProfile.fromClassName(className)
        );
    }

    private static WinDef.HWND pickBest(
            WinDef.HWND root,
            int screenX,
            int screenY,
            Point rootClient,
            WinDef.HWND... candidates
    ) {
        Candidate best = new Candidate(root, Integer.MAX_VALUE, false, readClassName(root));
        for (WinDef.HWND candidate : candidates) {
            if (candidate == null || candidate.equals(root)) {
                continue;
            }
            if (!containsScreenPoint(candidate, screenX, screenY)) {
                continue;
            }
            best.consider(candidate, clientAreaSize(candidate), isUnityClass(readClassName(candidate)));
        }
        if (!best.hwnd.equals(root)) {
            return best.hwnd;
        }
        if (containsScreenPoint(root, screenX, screenY)) {
            return root;
        }
        return root;
    }

    private static WinDef.HWND findDescendantAtScreenPoint(WinDef.HWND root, int screenX, int screenY) {
        Candidate best = new Candidate(null, Integer.MAX_VALUE, false, "");
        forEachDescendant(root, hwnd -> {
            if (hwnd.equals(root) || !User32.INSTANCE.IsWindowVisible(hwnd)) {
                return;
            }
            if (!containsScreenPoint(hwnd, screenX, screenY)) {
                return;
            }
            best.consider(hwnd, clientAreaSize(hwnd), isUnityClass(readClassName(hwnd)));
        });
        return best.hwnd;
    }

    static void forEachDescendant(WinDef.HWND root, Consumer<WinDef.HWND> visitor) {
        User32.INSTANCE.EnumChildWindows(root, (hwnd, data) -> {
            visitor.accept(hwnd);
            forEachDescendant(hwnd, visitor);
            return true;
        }, null);
    }

    private static WinDef.HWND deepestChildUnderRoot(WinDef.HWND root, int screenX, int screenY) {
        WinUser.POINT screen = new WinUser.POINT();
        screen.x = screenX;
        screen.y = screenY;
        WinDef.HWND hit = Win32Extras.User32Extra.INSTANCE.WindowFromPoint(screen);
        if (hit == null) {
            return null;
        }

        WinDef.HWND current = hit;
        WinDef.HWND best = null;
        while (current != null) {
            if (current.equals(root)) {
                return best;
            }
            if (Win32Extras.User32Extra.INSTANCE.IsChild(root, current)) {
                best = current;
            }
            current = User32.INSTANCE.GetParent(current);
        }
        return Win32Extras.User32Extra.INSTANCE.IsChild(root, hit) ? hit : null;
    }

    static boolean containsScreenPoint(WinDef.HWND hwnd, int screenX, int screenY) {
        Rectangle bounds = clientBoundsOnScreen(hwnd);
        return bounds.contains(screenX, screenY);
    }

    private static Rectangle clientBoundsOnScreen(WinDef.HWND hwnd) {
        WinUser.RECT clientRect = new WinUser.RECT();
        if (!User32.INSTANCE.GetClientRect(hwnd, clientRect)) {
            return new Rectangle();
        }
        int width = clientRect.right - clientRect.left;
        int height = clientRect.bottom - clientRect.top;
        if (width <= 0 || height <= 0) {
            return new Rectangle();
        }

        WinUser.POINT topLeft = new WinUser.POINT();
        topLeft.x = 0;
        topLeft.y = 0;
        if (!Win32Extras.User32Extra.INSTANCE.ClientToScreen(hwnd, topLeft)) {
            return new Rectangle();
        }
        return new Rectangle(topLeft.x, topLeft.y, width, height);
    }

    private static int clientAreaSize(WinDef.HWND hwnd) {
        Rectangle bounds = clientBoundsOnScreen(hwnd);
        return Math.max(1, bounds.width * bounds.height);
    }

    static boolean isUnityClass(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        String lower = className.toLowerCase();
        return lower.contains("unity")
                || lower.contains("hwndwrapper")
                || lower.contains("glfw");
    }

    static String readClassName(WinDef.HWND hwnd) {
        char[] buffer = new char[CLASS_NAME_MAX];
        int length = User32.INSTANCE.GetClassName(hwnd, buffer, buffer.length);
        if (length <= 0) {
            return "";
        }
        return Native.toString(buffer).trim();
    }

    private static WinUser.POINT toPoint(Point point) {
        WinUser.POINT winPoint = new WinUser.POINT();
        winPoint.x = point.x;
        winPoint.y = point.y;
        return winPoint;
    }

    private static final class Candidate {
        private WinDef.HWND hwnd;
        private int area;
        private boolean unity;

        private Candidate(WinDef.HWND hwnd, int area, boolean unity, String ignoredClass) {
            this.hwnd = hwnd;
            this.area = area;
            this.unity = unity;
        }

        private void consider(WinDef.HWND candidate, int candidateArea, boolean candidateUnity) {
            if (hwnd == null) {
                hwnd = candidate;
                area = candidateArea;
                unity = candidateUnity;
                return;
            }
            if (candidateUnity && !unity) {
                hwnd = candidate;
                area = candidateArea;
                unity = true;
                return;
            }
            if (candidateUnity == unity && candidateArea < area) {
                hwnd = candidate;
                area = candidateArea;
            }
        }
    }
}
