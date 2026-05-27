package com.auto.vision;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Counts endpoints on the outer wall skeleton (primary closure signal).
 * <p>
 * Pipeline: wall mask → skeleton → spur prune → largest exterior-facing component → endpoint count.
 * Endpoint: skeleton pixel with exactly one skeleton neighbor in 3x3 (8-connected).
 */
public final class WallEndpointCounter {

    private static final int MIN_SPUR_LENGTH = 4;

    private WallEndpointCounter() {
    }

    public static WallEndpointAnalysis analyze(Mat gray, double wallThreshold) {
        return analyze(gray, wallThreshold, null, null);
    }

    public static WallEndpointAnalysis analyze(Mat gray, double wallThreshold, boolean[][] exteriorWalkable) {
        return analyze(gray, wallThreshold, exteriorWalkable, null);
    }

    public static WallEndpointAnalysis analyze(
            Mat gray,
            double wallThreshold,
            boolean[][] exteriorWalkable,
            boolean[][] interiorWalkable
    ) {
        Mat wall = buildWallMask(gray, wallThreshold);
        int wallPixels = Core.countNonZero(wall);
        if (wallPixels <= 0) {
            return new WallEndpointAnalysis(0, 0, 0, List.of(), null);
        }

        Mat skeleton = skeletonize(wall);
        boolean[][] bone = matToBoolean(skeleton);
        bone = pruneSpurs(bone, MIN_SPUR_LENGTH);

        boolean[][] boundaryMask = buildBoundaryMask(bone, exteriorWalkable, interiorWalkable);
        if (countTrue(boundaryMask) <= 0) {
            boundaryMask = bone;
        }

        List<int[]> endpoints = findEndpoints(boundaryMask);
        int rawCount = findEndpoints(bone).size();
        return new WallEndpointAnalysis(wallPixels, rawCount, endpoints.size(), endpoints, skeleton);
    }

    static boolean isEndpoint(boolean[][] wall, int rows, int cols, int x, int y) {
        if (!wall[y][x]) {
            return false;
        }
        return countWallNeighbors(wall, rows, cols, x, y) == 1;
    }

    private static Mat buildWallMask(Mat gray, double wallThreshold) {
        Mat wall = new Mat();
        Imgproc.threshold(gray, wall, wallThreshold, 255, Imgproc.THRESH_BINARY);
        return wall;
    }

    private static Mat skeletonize(Mat wall) {
        Mat skeleton = Mat.zeros(wall.size(), wall.type());
        Mat current = wall.clone();
        Mat eroded = new Mat();
        Mat opened = new Mat();
        Mat temp = new Mat();
        Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS, new Size(3, 3));

        while (Core.countNonZero(current) > 0) {
            Imgproc.erode(current, eroded, element);
            Imgproc.dilate(eroded, opened, element);
            Core.subtract(current, opened, temp);
            Core.bitwise_or(skeleton, temp, skeleton);
            eroded.copyTo(current);
        }
        return skeleton;
    }

    private static boolean[][] buildBoundaryMask(
            boolean[][] skeleton,
            boolean[][] exteriorWalkable,
            boolean[][] interiorWalkable
    ) {
        int rows = skeleton.length;
        int cols = skeleton[0].length;
        boolean[][] boundary = new boolean[rows][cols];
        if (exteriorWalkable != null) {
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (skeleton[y][x] && touchesWalkable(exteriorWalkable, rows, cols, x, y)) {
                        boundary[y][x] = true;
                    }
                }
            }
            return boundary;
        }
        if (interiorWalkable != null) {
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (skeleton[y][x] && touchesWalkable(interiorWalkable, rows, cols, x, y)) {
                        boundary[y][x] = true;
                    }
                }
            }
            return boundary;
        }
        return copy(skeleton);
    }

    private static boolean touchesWalkable(boolean[][] walkable, int rows, int cols, int x, int y) {
        if (walkable[y][x]) {
            return true;
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (nx >= 0 && ny >= 0 && nx < cols && ny < rows && walkable[ny][nx]) {
                return true;
            }
        }
        return false;
    }

    private static boolean[][] selectLargestComponent(boolean[][] mask) {
        int rows = mask.length;
        int cols = mask[0].length;
        boolean[][] visited = new boolean[rows][cols];
        boolean[][] largest = new boolean[rows][cols];
        int largestSize = 0;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (!mask[y][x] || visited[y][x]) {
                    continue;
                }
                List<int[]> component = new ArrayList<>();
                floodComponent(mask, visited, cols, rows, x, y, component);
                if (component.size() > largestSize) {
                    largestSize = component.size();
                    largest = new boolean[rows][cols];
                    for (int[] point : component) {
                        largest[point[1]][point[0]] = true;
                    }
                }
            }
        }
        return largest;
    }

    private static void floodComponent(
            boolean[][] mask,
            boolean[][] visited,
            int cols,
            int rows,
            int startX,
            int startY,
            List<int[]> component
    ) {
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {startX, startY});
        visited[startY][startX] = true;
        while (!queue.isEmpty()) {
            int[] point = queue.poll();
            component.add(point);
            int x = point[0];
            int y = point[1];
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= cols || ny >= rows || visited[ny][nx] || !mask[ny][nx]) {
                        continue;
                    }
                    visited[ny][nx] = true;
                    queue.add(new int[] {nx, ny});
                }
            }
        }
    }

    private static boolean[][] pruneSpurs(boolean[][] skeleton, int maxSpurLength) {
        boolean[][] bone = copy(skeleton);
        boolean changed = true;
        while (changed) {
            changed = false;
            List<int[]> endpoints = findEndpoints(bone);
            for (int[] endpoint : endpoints) {
                List<int[]> spur = traceSpur(bone, endpoint[0], endpoint[1], maxSpurLength + 1);
                if (spur.size() <= maxSpurLength && !spur.isEmpty()) {
                    for (int[] point : spur) {
                        bone[point[1]][point[0]] = false;
                    }
                    changed = true;
                }
            }
        }
        return bone;
    }

    private static List<int[]> traceSpur(boolean[][] bone, int startX, int startY, int maxSteps) {
        List<int[]> spur = new ArrayList<>();
        int x = startX;
        int y = startY;
        int prevX = -1;
        int prevY = -1;
        for (int step = 0; step < maxSteps; step++) {
            spur.add(new int[] {x, y});
            List<int[]> next = skeletonNeighbors(bone, x, y, prevX, prevY);
            if (next.isEmpty()) {
                break;
            }
            if (next.size() > 1 || countWallNeighbors(bone, bone.length, bone[0].length, x, y) > 2) {
                break;
            }
            int[] forward = next.get(0);
            prevX = x;
            prevY = y;
            x = forward[0];
            y = forward[1];
        }
        return spur;
    }

    private static List<int[]> skeletonNeighbors(
            boolean[][] bone,
            int x,
            int y,
            int prevX,
            int prevY
    ) {
        int rows = bone.length;
        int cols = bone[0].length;
        List<int[]> neighbors = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (nx == prevX && ny == prevY) {
                    continue;
                }
                if (nx >= 0 && ny >= 0 && nx < cols && ny < rows && bone[ny][nx]) {
                    neighbors.add(new int[] {nx, ny});
                }
            }
        }
        return neighbors;
    }

    private static List<int[]> findEndpoints(boolean[][] wall) {
        int rows = wall.length;
        int cols = wall[0].length;
        List<int[]> endpoints = new ArrayList<>();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (isEndpoint(wall, rows, cols, x, y)) {
                    endpoints.add(new int[] {x, y});
                }
            }
        }
        return endpoints;
    }

    private static int countWallNeighbors(boolean[][] wall, int rows, int cols, int x, int y) {
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < cols && ny < rows && wall[ny][nx]) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean[][] matToBoolean(Mat mat) {
        int rows = mat.rows();
        int cols = mat.cols();
        boolean[][] values = new boolean[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                values[y][x] = mat.get(y, x)[0] > 0;
            }
        }
        return values;
    }

    private static boolean[][] copy(boolean[][] source) {
        boolean[][] copy = new boolean[source.length][source[0].length];
        for (int y = 0; y < source.length; y++) {
            System.arraycopy(source[y], 0, copy[y], 0, source[y].length);
        }
        return copy;
    }

    private static int countTrue(boolean[][] mask) {
        int count = 0;
        for (boolean[] row : mask) {
            for (boolean value : row) {
                if (value) {
                    count++;
                }
            }
        }
        return count;
    }

    public record WallEndpointAnalysis(
            int wallPixels,
            int rawEndpointCount,
            int endpointCount,
            List<int[]> endpoints,
            Mat skeleton
    ) {
        public boolean closed() {
            return endpointCount == 0;
        }

        public String summary() {
            if (wallPixels <= 0) {
                return "端点计数：未检测到白色墙体。";
            }
            if (closed()) {
                return "外轮廓端点：0 个，墙体闭合。";
            }
            return "外轮廓端点：" + endpointCount + " 个（原始 " + rawEndpointCount + "），墙体未闭合。";
        }
    }
}
