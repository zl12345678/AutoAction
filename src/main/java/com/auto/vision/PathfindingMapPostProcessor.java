package com.auto.vision;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Post-processes a grayscale/binary map so walkable areas are enclosed and exterior margins cannot be used by A*.
 */
final class PathfindingMapPostProcessor {
    private PathfindingMapPostProcessor() {
    }

    static void apply(
            Mat map,
            int morphCloseKernelSize,
            int sealBorderWidth,
            boolean sealExterior,
            double walkableThreshold
    ) {
        if (map == null || map.empty() || map.channels() != 1) {
            throw new IllegalArgumentException("Pathfinding map must be a single-channel Mat");
        }
        if (morphCloseKernelSize > 0) {
            closeWallGaps(map, morphCloseKernelSize);
        }
        if (sealExterior) {
            sealExteriorWalkable(map, walkableThreshold);
        }
        if (sealBorderWidth > 0) {
            sealBorder(map, sealBorderWidth);
        }
    }

    /**
     * Closes small breaks in bright obstacle regions (walls).
     */
    static void closeWallGaps(Mat map, int kernelSize) {
        int size = Math.max(3, kernelSize | 1);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(size, size));
        Imgproc.morphologyEx(map, map, Imgproc.MORPH_CLOSE, kernel);
    }

    /**
     * Forces a band along the image edge to be impassable.
     */
    static void sealBorder(Mat map, int width) {
        int rows = map.rows();
        int cols = map.cols();
        int band = Math.min(width, Math.min(rows, cols) / 2);
        if (band <= 0) {
            return;
        }
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (x < band || x >= cols - band || y < band || y >= rows - band) {
                    map.put(y, x, 255);
                }
            }
        }
    }

    /**
     * Marks walkable pixels connected to the image border as obstacles (exterior / UI margins).
     * Interior rooms separated by closed walls stay walkable.
     */
    static void sealExteriorWalkable(Mat map, double walkableThreshold) {
        int rows = map.rows();
        int cols = map.cols();
        boolean[][] visited = new boolean[rows][cols];
        Queue<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < cols; x++) {
            enqueueWalkable(map, visited, queue, x, 0, walkableThreshold);
            enqueueWalkable(map, visited, queue, x, rows - 1, walkableThreshold);
        }
        for (int y = 0; y < rows; y++) {
            enqueueWalkable(map, visited, queue, 0, y, walkableThreshold);
            enqueueWalkable(map, visited, queue, cols - 1, y, walkableThreshold);
        }

        while (!queue.isEmpty()) {
            int[] point = queue.poll();
            int x = point[0];
            int y = point[1];
            map.put(y, x, 255);
            enqueueWalkable(map, visited, queue, x - 1, y, walkableThreshold);
            enqueueWalkable(map, visited, queue, x + 1, y, walkableThreshold);
            enqueueWalkable(map, visited, queue, x, y - 1, walkableThreshold);
            enqueueWalkable(map, visited, queue, x, y + 1, walkableThreshold);
        }
    }

    private static void enqueueWalkable(
            Mat map,
            boolean[][] visited,
            Queue<int[]> queue,
            int x,
            int y,
            double walkableThreshold
    ) {
        if (x < 0 || y < 0 || x >= map.cols() || y >= map.rows()) {
            return;
        }
        if (visited[y][x]) {
            return;
        }
        if (map.get(y, x)[0] > walkableThreshold) {
            return;
        }
        visited[y][x] = true;
        queue.add(new int[] {x, y});
    }
}
