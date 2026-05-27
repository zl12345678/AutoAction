package com.auto.vision;

import org.opencv.core.Mat;
import org.opencv.core.Point;
/**
 * Post-processes raw map-match coordinates (walkable snap).
 */
public final class LocalizationRefiner {
    private LocalizationRefiner() {
    }

    /**
     * Moves the point to the nearest walkable pixel (dark) on the pathfinding map.
     */
    public static Point snapToWalkable(Mat pathfindingMap, Point point, double walkableThreshold, int maxRadius) {
        if (pathfindingMap == null || pathfindingMap.empty() || point == null) {
            return point;
        }
        int cx = clamp((int) Math.round(point.x), 0, pathfindingMap.cols() - 1);
        int cy = clamp((int) Math.round(point.y), 0, pathfindingMap.rows() - 1);
        if (isWalkable(pathfindingMap, cx, cy, walkableThreshold)) {
            return new Point(cx, cy);
        }

        int limit = Math.max(1, maxRadius);
        for (int radius = 1; radius <= limit; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) {
                        continue;
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    if (x < 0 || y < 0 || x >= pathfindingMap.cols() || y >= pathfindingMap.rows()) {
                        continue;
                    }
                    if (isWalkable(pathfindingMap, x, y, walkableThreshold)) {
                        return new Point(x, y);
                    }
                }
            }
        }
        return point;
    }

    private static boolean isWalkable(Mat pathfindingMap, int x, int y, double walkableThreshold) {
        double[] pixel = pathfindingMap.get(y, x);
        if (pixel == null || pixel.length == 0) {
            return false;
        }
        return pixel[0] < walkableThreshold;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
