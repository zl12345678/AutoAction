package com.auto.window;

import java.awt.Point;
import java.awt.Rectangle;

public final class WindowClickGeometry {
    private static final double MIN_DISTANCE_FROM_CENTER = 120.0;
    private static final int CLIENT_MARGIN = 8;

    private WindowClickGeometry() {
    }

    public static Point prepareScreenClick(Point screenPoint, Rectangle clientBoundsOnScreen) {
        Point center = new Point(
                (int) Math.round(clientBoundsOnScreen.getCenterX()),
                (int) Math.round(clientBoundsOnScreen.getCenterY())
        );
        Point adjusted = ensureMinDistanceFromCenter(screenPoint, center, MIN_DISTANCE_FROM_CENTER);
        return clampToClient(adjusted, clientBoundsOnScreen);
    }

    public static Point clampToClient(Point screenPoint, Rectangle clientBoundsOnScreen) {
        int minX = clientBoundsOnScreen.x + CLIENT_MARGIN;
        int minY = clientBoundsOnScreen.y + CLIENT_MARGIN;
        int maxX = clientBoundsOnScreen.x + clientBoundsOnScreen.width - CLIENT_MARGIN - 1;
        int maxY = clientBoundsOnScreen.y + clientBoundsOnScreen.height - CLIENT_MARGIN - 1;
        if (maxX < minX || maxY < minY) {
            return new Point(screenPoint);
        }
        int x = Math.max(minX, Math.min(maxX, (int) Math.round(screenPoint.getX())));
        int y = Math.max(minY, Math.min(maxY, (int) Math.round(screenPoint.getY())));
        return new Point(x, y);
    }

    static Point ensureMinDistanceFromCenter(Point screenPoint, Point center, double minDistance) {
        double dx = screenPoint.getX() - center.getX();
        double dy = screenPoint.getY() - center.getY();
        double distance = Math.hypot(dx, dy);
        if (distance >= minDistance) {
            return new Point(screenPoint);
        }
        if (distance < 1.0) {
            return new Point(
                    (int) Math.round(center.getX() + minDistance),
                    (int) Math.round(center.getY())
            );
        }
        double scale = minDistance / distance;
        return new Point(
                (int) Math.round(center.getX() + dx * scale),
                (int) Math.round(center.getY() + dy * scale)
        );
    }
}
