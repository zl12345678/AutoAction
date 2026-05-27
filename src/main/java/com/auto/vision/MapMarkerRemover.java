package com.auto.vision;

import com.auto.config.HsvColorRange;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import java.util.ArrayDeque;
import java.util.Queue;

final class MapMarkerRemover {
  private MapMarkerRemover() {
  }

  static void removeColorRange(Mat bgrImage, HsvColorRange range) {
    Mat hsvImage = new Mat();
    Imgproc.cvtColor(bgrImage, hsvImage, Imgproc.COLOR_BGR2HSV);
    Mat mask = new Mat();
    Core.inRange(
        hsvImage,
        new Scalar(range.hueMin(), range.satMin(), range.valMin()),
        new Scalar(range.hueMax(), 255, 255),
        mask
    );
    Mat keepMask = new Mat();
    Core.bitwise_not(mask, keepMask);
    Mat filtered = new Mat();
    Core.bitwise_and(bgrImage, bgrImage, filtered, keepMask);
    filtered.copyTo(bgrImage);
  }

  static void inpaintMarkerMask(Mat bgrImage, Mat markerMask) {
    if (bgrImage == null || bgrImage.empty() || markerMask == null || markerMask.empty()) {
      return;
    }
    if (Core.countNonZero(markerMask) == 0) {
      return;
    }
    Mat repaired = new Mat();
    Photo.inpaint(bgrImage, markerMask, repaired, 2, Photo.INPAINT_TELEA);
    repaired.copyTo(bgrImage);
  }

  static Mat buildPlayerMarkerMask(Mat bgrImage, HsvColorRange range) {
    Mat hsvImage = new Mat();
    Imgproc.cvtColor(bgrImage, hsvImage, Imgproc.COLOR_BGR2HSV);
    Mat mask = new Mat();
    Core.inRange(
        hsvImage,
        new Scalar(range.hueMin(), range.satMin(), range.valMin()),
        new Scalar(range.hueMax(), 255, 255),
        mask
    );
    Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
    Imgproc.dilate(mask, mask, kernel);
    return mask;
  }

  static void inpaintCircularRegion(Mat bgrImage, Point center, int radius) {
    if (center == null || bgrImage == null || bgrImage.empty()) {
      return;
    }
    int x = (int) Math.round(center.x);
    int y = (int) Math.round(center.y);
    if (x < 0 || y < 0 || x >= bgrImage.cols() || y >= bgrImage.rows()) {
      return;
    }
    Mat mask = Mat.zeros(bgrImage.size(), CvType.CV_8UC1);
    Imgproc.circle(mask, center, Math.max(1, radius), new Scalar(255), -1);
    Mat repaired = new Mat();
    Photo.inpaint(bgrImage, mask, repaired, 3, Photo.INPAINT_TELEA);
    repaired.copyTo(bgrImage);
  }

  static Mat removeSmallComponents(Mat binary, int minArea) {
    if (binary == null || binary.empty() || minArea <= 1) {
      return binary == null ? new Mat() : binary.clone();
    }
    Mat labels = new Mat();
    Mat stats = new Mat();
    Mat centroids = new Mat();
    int count = Imgproc.connectedComponentsWithStats(binary, labels, stats, centroids, 8, CvType.CV_32S);
    Mat output = binary.clone();
    for (int label = 1; label < count; label++) {
      if (stats.get(label, Imgproc.CC_STAT_AREA)[0] < minArea) {
        Mat componentMask = new Mat();
        Core.compare(labels, new Scalar(label), componentMask, Core.CMP_EQ);
        output.setTo(new Scalar(0), componentMask);
      }
    }
    return output;
  }

  /**
   * Converts minimap grayscale to pathfinding polarity: black walkable, white non-walkable.
   */
  static Mat toPathfindingPolarity(Mat gray, int fogThreshold) {
    Mat explored = new Mat();
    Imgproc.threshold(gray, explored, fogThreshold, 255, Imgproc.THRESH_BINARY);
    Mat inverted = new Mat();
    Core.bitwise_not(explored, inverted);
    return inverted;
  }

  static Rect boundingBoxOfExploredComponent(Mat pathfindingBinary, Point seed, int padding) {
    if (pathfindingBinary == null || pathfindingBinary.empty() || seed == null) {
      return new Rect(0, 0, pathfindingBinary == null ? 0 : pathfindingBinary.cols(),
          pathfindingBinary == null ? 0 : pathfindingBinary.rows());
    }
    Point walkableSeed = findWalkableSeed(pathfindingBinary, seed);
    int cols = pathfindingBinary.cols();
    int rows = pathfindingBinary.rows();
    int startX = (int) Math.round(walkableSeed.x);
    int startY = (int) Math.round(walkableSeed.y);
  if (!isWalkable(pathfindingBinary, startX, startY)) {
      return new Rect(0, 0, cols, rows);
    }

    boolean[][] visited = new boolean[rows][cols];
    Queue<int[]> queue = new ArrayDeque<>();
    queue.add(new int[] {startX, startY});
    visited[startY][startX] = true;
    int minX = startX;
    int maxX = startX;
    int minY = startY;
    int maxY = startY;

    while (!queue.isEmpty()) {
      int[] current = queue.poll();
      int x = current[0];
      int y = current[1];
      minX = Math.min(minX, x);
      maxX = Math.max(maxX, x);
      minY = Math.min(minY, y);
      maxY = Math.max(maxY, y);
      visitWalkableNeighbor(pathfindingBinary, visited, queue, x - 1, y);
      visitWalkableNeighbor(pathfindingBinary, visited, queue, x + 1, y);
      visitWalkableNeighbor(pathfindingBinary, visited, queue, x, y - 1);
      visitWalkableNeighbor(pathfindingBinary, visited, queue, x, y + 1);
    }

    int x = Math.max(0, minX - padding);
    int y = Math.max(0, minY - padding);
    int width = Math.min(cols - x, maxX - minX + 1 + padding * 2);
    int height = Math.min(rows - y, maxY - minY + 1 + padding * 2);
    return new Rect(x, y, Math.max(1, width), Math.max(1, height));
  }

  private static void visitWalkableNeighbor(
      Mat pathfindingBinary,
      boolean[][] visited,
      Queue<int[]> queue,
      int x,
      int y
  ) {
    if (x < 0 || y < 0 || x >= pathfindingBinary.cols() || y >= pathfindingBinary.rows()) {
      return;
    }
    if (visited[y][x] || !isWalkable(pathfindingBinary, x, y)) {
      return;
    }
    visited[y][x] = true;
    queue.add(new int[] {x, y});
  }

  private static Point findWalkableSeed(Mat pathfindingBinary, Point preferred) {
    int px = clamp((int) Math.round(preferred.x), 0, pathfindingBinary.cols() - 1);
    int py = clamp((int) Math.round(preferred.y), 0, pathfindingBinary.rows() - 1);
    if (isWalkable(pathfindingBinary, px, py)) {
      return new Point(px, py);
    }
    for (int radius = 1; radius <= 10; radius++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
          if (Math.abs(dx) != radius && Math.abs(dy) != radius) {
            continue;
          }
          int x = px + dx;
          int y = py + dy;
          if (isWalkable(pathfindingBinary, x, y)) {
            return new Point(x, y);
          }
        }
      }
    }
    return new Point(px, py);
  }

  private static boolean isWalkable(Mat pathfindingBinary, int x, int y) {
    if (x < 0 || y < 0 || x >= pathfindingBinary.cols() || y >= pathfindingBinary.rows()) {
      return false;
    }
    return pathfindingBinary.get(y, x)[0] <= 127;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  static Mat morphologicalGradient(Mat binary) {
    Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
    Mat gradient = new Mat();
    Imgproc.morphologyEx(binary, gradient, Imgproc.MORPH_GRADIENT, kernel);
    return gradient;
  }

  /**
   * Marks walkable pixels (black) that touch an obstacle (white). Matches in-game minimap borders.
   */
  static Mat extractWalkableAdjacentBoundary(Mat binary) {
    if (binary == null || binary.empty()) {
      return new Mat();
    }
    Mat boundary = Mat.zeros(binary.size(), CvType.CV_8UC1);
    int rows = binary.rows();
    int cols = binary.cols();
    for (int y = 1; y < rows - 1; y++) {
      for (int x = 1; x < cols - 1; x++) {
        if (binary.get(y, x)[0] > 127) {
          continue;
        }
        if (isObstacle(binary, x - 1, y)
            || isObstacle(binary, x + 1, y)
            || isObstacle(binary, x, y - 1)
            || isObstacle(binary, x, y + 1)) {
          boundary.put(y, x, 255);
        }
      }
    }
    return boundary;
  }

  static Mat extractMinimapBoundaryFeatures(Mat binary) {
    if (binary == null || binary.empty()) {
      return new Mat();
    }
    Mat gradient = morphologicalGradient(binary);
    Mat combined = new Mat();
    Core.bitwise_or(binary, gradient, combined);
    return removeSmallComponents(combined, 8);
  }

  static boolean isPathfindingBinary(Mat binary) {
    if (binary == null || binary.empty() || binary.channels() != 1) {
      return false;
    }
    int walkable = 0;
    int obstacle = 0;
    int total = binary.rows() * binary.cols();
    for (int y = 0; y < binary.rows(); y++) {
      for (int x = 0; x < binary.cols(); x++) {
        if (binary.get(y, x)[0] <= 127) {
          walkable++;
        } else {
          obstacle++;
        }
      }
    }
    return walkable > obstacle && walkable > total * 0.45;
  }

  private static boolean isObstacle(Mat binary, int x, int y) {
    return binary.get(y, x)[0] > 127;
  }

  static Mat thicken(Mat binary, int kernelSize) {
    if (binary == null || binary.empty()) {
      return new Mat();
    }
    Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(kernelSize, kernelSize));
    Mat thickened = new Mat();
    Imgproc.dilate(binary, thickened, kernel);
    return thickened;
  }
}
