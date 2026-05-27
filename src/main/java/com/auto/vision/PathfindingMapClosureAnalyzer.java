package com.auto.vision;

import com.auto.config.MapClosureConfig;
import com.auto.opencv.utils.ImageProcessor;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Tests wall contour closure via endpoint counting (primary) and border flood (supplementary).
 * <p>
 * Primary: skeletonize white walls; endpoint = wall pixel with exactly one wall neighbor in 3x3.
 * 0 endpoints → closed; ≥ 2 endpoints → not closed.
 */
public final class PathfindingMapClosureAnalyzer {

  private static final int MIN_WALL_PIXELS = 8;
  /** Interior walkable ratio above this suggests a sealed dungeon (closed). */
  private static final double MIN_SEALED_INTERIOR_RATIO = 0.10;
  /** Interior ratio below this means exterior/interior black are connected (not closed). */
  private static final double MAX_CONNECTED_INTERIOR_RATIO = 0.08;
  /** Tolerate this many boundary endpoints as skeleton noise on sealed maps. */
  private static final int ENDPOINT_NOISE_TOLERANCE = 6;

  public PathfindingMapClosureAnalysis analyze(BufferedImage pathfindingMap, MapClosureConfig config) {
    if (pathfindingMap == null) {
      return failure("请先生成或导入寻路地图。");
    }
    OpenCvLoader.load();
    Mat gray = toGrayMat(pathfindingMap);
    return analyzeTopDown(gray, config);
  }

  public PathfindingMapClosureAnalysis previewSeal(BufferedImage pathfindingMap, MapClosureConfig config) {
    if (pathfindingMap == null) {
      return failure("请先生成或导入寻路地图。");
    }
    OpenCvLoader.load();
    Mat gray = toGrayMat(pathfindingMap);
    Mat sealed = gray.clone();
    PathfindingMapPostProcessor.apply(
        sealed,
        config.morphCloseKernelSize(),
        config.sealBorderWidth(),
        config.sealExterior(),
        config.walkableThreshold()
    );
    BufferedImage sealedImage = ImageProcessor.matToBufferedImage(sealed);
        PathfindingMapClosureAnalysis afterSeal = analyzeTopDown(sealed, config);
        return new PathfindingMapClosureAnalysis(
                afterSeal.closed(),
                afterSeal.walkablePixels(),
                afterSeal.walkableAfterExteriorFlood(),
                afterSeal.exteriorFloodedPixels(),
                afterSeal.borderWalkable(),
                afterSeal.leakX(),
                afterSeal.leakY(),
                afterSeal.leakPreviewImage(),
                sealedImage,
                "密封预览：" + afterSeal.message(),
                afterSeal.floodTrace(),
                afterSeal.wallEndpointCount(),
                afterSeal.wallPixels()
        );
  }

  private static PathfindingMapClosureAnalysis analyzeTopDown(Mat gray, MapClosureConfig config) {
    int rows = gray.rows();
    int cols = gray.cols();
    double threshold = config.walkableThreshold();
    boolean[][] walkable = buildWalkable(gray, threshold);
    int walkableBefore = countTrue(walkable);

    if (walkableBefore <= 0) {
      return failure("未检测到可走区域（全白或阈值过高）。");
    }

    boolean borderWalkable = hasBorderWalkable(walkable, rows, cols);
    boolean[][] exteriorWalkable = null;
    PathfindingMapClosureFloodTrace trace;
    boolean[][] exteriorFlooded = null;
    boolean[][] interior = null;
    int interiorCount = 0;
    int floodedCount = 0;

    if (!borderWalkable) {
      BufferedImage base = ImageProcessor.matToBufferedImage(gray);
      trace = buildFloodTrace(base, walkable, rows, cols, List.of(), walkableMask(walkable), false);
      interior = walkableMask(walkable);
      interiorCount = walkableBefore;
    } else {
      FloodOrder floodOrder = collectFloodOrder(walkable, rows, cols);
      exteriorFlooded = floodOrder.flooded();
      exteriorWalkable = exteriorFlooded;
      floodedCount = countTrue(exteriorFlooded);
      interior = buildInteriorMask(walkable, exteriorFlooded);
      interiorCount = countTrue(interior);
      BufferedImage base = ImageProcessor.matToBufferedImage(gray);
      trace = buildFloodTrace(
          base,
          walkable,
          rows,
          cols,
          floodOrder.processedPoints(),
          interior,
          true
      );
    }

    WallEndpointCounter.WallEndpointAnalysis endpointAnalysis = borderWalkable
        ? WallEndpointCounter.analyze(gray, threshold, exteriorWalkable, null)
        : WallEndpointCounter.analyze(gray, threshold, null, walkable);
    List<int[]> wallEndpoints = endpointAnalysis.endpoints();
    double interiorRatio = interiorCount / (double) walkableBefore;
    boolean closed = decideClosed(
        endpointAnalysis,
        interiorRatio,
        borderWalkable,
        interiorCount
    );
    int[] leak = pickLeakPoint(wallEndpoints, interior, walkable, borderWalkable, rows, cols, closed);

    String message = buildMessage(
        endpointAnalysis,
        closed,
        borderWalkable,
        interiorCount,
        walkableBefore,
        floodedCount,
        trace
    );

    BufferedImage preview = buildClosedPreview(gray, exteriorFlooded, interior, wallEndpoints, leak[0], leak[1]);
    if (!borderWalkable) {
      return new PathfindingMapClosureAnalysis(
          closed,
          walkableBefore,
          interiorCount,
          floodedCount,
          false,
          leak[0],
          leak[1],
          preview,
          null,
          message,
          trace,
          endpointAnalysis.endpointCount(),
          endpointAnalysis.wallPixels()
      );
    }

    return new PathfindingMapClosureAnalysis(
        closed,
        walkableBefore,
        interiorCount,
        floodedCount,
        true,
        leak[0],
        leak[1],
        preview,
        null,
        message,
        trace,
        endpointAnalysis.endpointCount(),
        endpointAnalysis.wallPixels()
    );
  }

  private static boolean decideClosed(
      WallEndpointCounter.WallEndpointAnalysis endpointAnalysis,
      double interiorRatio,
      boolean borderWalkable,
      int interiorCount
  ) {
    if (endpointAnalysis.wallPixels() < MIN_WALL_PIXELS) {
      return false;
    }
    int endpoints = endpointAnalysis.endpointCount();

    if (borderWalkable && interiorRatio < MAX_CONNECTED_INTERIOR_RATIO) {
      return false;
    }
    if (borderWalkable && interiorRatio >= MIN_SEALED_INTERIOR_RATIO) {
      return endpoints <= ENDPOINT_NOISE_TOLERANCE;
    }
    if (endpoints >= 2) {
      return false;
    }
    if (!borderWalkable) {
      return endpoints == 0;
    }
    return endpoints == 0 && interiorCount > 0;
  }

  private static int[] pickLeakPoint(
      List<int[]> wallEndpoints,
      boolean[][] interior,
      boolean[][] walkable,
      boolean borderWalkable,
      int rows,
      int cols,
      boolean closed
  ) {
    if (!wallEndpoints.isEmpty()) {
      int[] point = wallEndpoints.get(0);
      return new int[] {point[0], point[1]};
    }
    if (closed) {
      return new int[] {-1, -1};
    }
    int[] interiorPixel = firstInteriorPixel(interior, rows, cols);
    if (interiorPixel[0] >= 0) {
      return interiorPixel;
    }
    if (borderWalkable) {
      return firstBorderWalkable(walkable, rows, cols);
    }
    return new int[] {-1, -1};
  }

  private static int[] firstBorderWalkable(boolean[][] walkable, int rows, int cols) {
    for (int x = 0; x < cols; x++) {
      if (walkable[0][x]) {
        return new int[] {x, 0};
      }
      if (walkable[rows - 1][x]) {
        return new int[] {x, rows - 1};
      }
    }
    for (int y = 0; y < rows; y++) {
      if (walkable[y][0]) {
        return new int[] {0, y};
      }
      if (walkable[y][cols - 1]) {
        return new int[] {cols - 1, y};
      }
    }
    return new int[] {-1, -1};
  }

  private static String buildMessage(
      WallEndpointCounter.WallEndpointAnalysis endpointAnalysis,
      boolean closed,
      boolean borderWalkable,
      int interiorCount,
      int walkableBefore,
      int floodedCount,
      PathfindingMapClosureFloodTrace trace
  ) {
    StringBuilder message = new StringBuilder("闭合检测：");
    message.append(endpointAnalysis.summary());
    if (endpointAnalysis.wallPixels() < MIN_WALL_PIXELS) {
      message.append(" 墙体像素过少。");
    } else if (closed) {
      message.append(" ✅ 外轮廓端点≤").append(ENDPOINT_NOISE_TOLERANCE);
      if (borderWalkable) {
        message.append("，内部隔离 ").append(percent(interiorCount / (double) walkableBefore));
      }
    } else {
      message.append(" ❌");
      if (endpointAnalysis.endpointCount() >= 2) {
        message.append(" 外轮廓端点≥2");
      } else if (borderWalkable && interiorCount / (double) walkableBefore < MAX_CONNECTED_INTERIOR_RATIO) {
        message.append(" 外部与内部黑色连通");
      }
      message.append("，请查看紫色端点并补墙。");
    }

    if (borderWalkable && trace != null) {
      double floodedRatio = floodedCount / (double) walkableBefore;
      double interiorRatio = interiorCount / (double) walkableBefore;
      message.append(" 泛洪参考：覆盖 ").append(percent(floodedRatio));
      if (interiorCount > 0) {
        message.append("，孤立内部 ").append(interiorCount).append(" px (").append(percent(interiorRatio)).append(")");
      }
      message.append("。").append(trace.globalSpanSummary());
      message.append("；").append(trace.incompleteRowCount()).append(" 行走可跨度未完全覆盖。");
    } else if (!borderWalkable) {
      message.append(" 四边均无可走像素，内部区域已被墙包围。");
    }
    return message.toString();
  }

  private static Mat toGrayMat(BufferedImage image) {
    Mat mat = ImageProcessor.bufferedImageToMat(image);
    if (mat.channels() == 1) {
      return mat;
    }
    Mat gray = new Mat();
    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
    return gray;
  }

  private static boolean[][] buildWalkable(Mat map, double walkableThreshold) {
    int rows = map.rows();
    int cols = map.cols();
    boolean[][] walkable = new boolean[rows][cols];
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        walkable[y][x] = map.get(y, x)[0] <= walkableThreshold;
      }
    }
    return walkable;
  }

  private static boolean[][] walkableMask(boolean[][] walkable) {
    boolean[][] copy = new boolean[walkable.length][walkable[0].length];
    for (int y = 0; y < walkable.length; y++) {
      System.arraycopy(walkable[y], 0, copy[y], 0, walkable[y].length);
    }
    return copy;
  }

  private static boolean[][] buildInteriorMask(boolean[][] walkable, boolean[][] exteriorFlooded) {
    int rows = walkable.length;
    int cols = walkable[0].length;
    boolean[][] interior = new boolean[rows][cols];
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        interior[y][x] = walkable[y][x] && !exteriorFlooded[y][x];
      }
    }
    return interior;
  }

  private static FloodOrder collectFloodOrder(boolean[][] walkable, int rows, int cols) {
    return collectFloodOrder(walkable, rows, cols, null);
  }

  private static FloodOrder collectFloodOrder(
      boolean[][] walkable,
      int rows,
      int cols,
      boolean[][] flooded
  ) {
    boolean[][] visited = flooded != null ? flooded : new boolean[rows][cols];
    PriorityQueue<int[]> queue = new PriorityQueue<>(topDownOrder());
    seedBorderWalkable(walkable, rows, cols, queue);
    List<int[]> processedPoints = new ArrayList<>();
    floodWalkable(walkable, rows, cols, queue, visited, processedPoints);
    return new FloodOrder(visited, processedPoints);
  }

  private static PathfindingMapClosureFloodTrace buildFloodTrace(
      BufferedImage baseImage,
      boolean[][] walkable,
      int rows,
      int cols,
      List<int[]> processedPoints,
      boolean[][] interior,
      boolean borderWalkable
  ) {
    int[] orderX = new int[processedPoints.size()];
    int[] orderY = new int[processedPoints.size()];
    for (int index = 0; index < processedPoints.size(); index++) {
      int[] point = processedPoints.get(index);
      orderX[index] = point[0];
      orderY[index] = point[1];
    }
    int[] prefixCountByRow = PathfindingMapClosureFloodTrace.buildPrefixCountByRow(rows, orderY);
    int[] walkableMinXAtRow = PathfindingMapClosureFloodTrace.buildWalkableMinXAtRow(walkable, rows, cols);
    int[] walkableMaxXAtRow = PathfindingMapClosureFloodTrace.buildWalkableMaxXAtRow(walkable, rows, cols);
    return new PathfindingMapClosureFloodTrace(
        baseImage,
        rows,
        cols,
        orderX,
        orderY,
        prefixCountByRow,
        walkableMinXAtRow,
        walkableMaxXAtRow,
        interior,
        borderWalkable
    );
  }

  private static void seedBorderWalkable(
      boolean[][] walkable,
      int rows,
      int cols,
      PriorityQueue<int[]> queue
  ) {
    for (int x = 0; x < cols; x++) {
      enqueueWalkable(walkable, rows, cols, queue, x, 0);
      enqueueWalkable(walkable, rows, cols, queue, x, rows - 1);
    }
    for (int y = 0; y < rows; y++) {
      enqueueWalkable(walkable, rows, cols, queue, 0, y);
      enqueueWalkable(walkable, rows, cols, queue, cols - 1, y);
    }
  }

  private static void floodWalkable(
      boolean[][] walkable,
      int rows,
      int cols,
      PriorityQueue<int[]> queue,
      boolean[][] flooded,
      List<int[]> processedPoints
  ) {
    while (!queue.isEmpty()) {
      int[] point = queue.poll();
      int x = point[0];
      int y = point[1];
      if (x < 0 || y < 0 || x >= cols || y >= rows || flooded[y][x] || !walkable[y][x]) {
        continue;
      }
      flooded[y][x] = true;
      processedPoints.add(new int[] {x, y});
      enqueueWalkable(walkable, rows, cols, queue, x - 1, y);
      enqueueWalkable(walkable, rows, cols, queue, x + 1, y);
      enqueueWalkable(walkable, rows, cols, queue, x, y + 1);
      enqueueWalkable(walkable, rows, cols, queue, x, y - 1);
    }
  }

  private static void enqueueWalkable(
      boolean[][] walkable,
      int rows,
      int cols,
      PriorityQueue<int[]> queue,
      int x,
      int y
  ) {
    if (x < 0 || y < 0 || x >= cols || y >= rows || !walkable[y][x]) {
      return;
    }
    queue.add(new int[] {x, y});
  }

  private static Comparator<int[]> topDownOrder() {
    return Comparator.comparingInt((int[] point) -> point[1]).thenComparingInt(point -> point[0]);
  }

  private static boolean hasBorderWalkable(boolean[][] walkable, int rows, int cols) {
    for (int x = 0; x < cols; x++) {
      if (walkable[0][x] || walkable[rows - 1][x]) {
        return true;
      }
    }
    for (int y = 0; y < rows; y++) {
      if (walkable[y][0] || walkable[y][cols - 1]) {
        return true;
      }
    }
    return false;
  }

  private static int[] firstInteriorPixel(boolean[][] interior, int rows, int cols) {
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        if (interior[y][x]) {
          return new int[] {x, y};
        }
      }
    }
    return new int[] {-1, -1};
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

  private static String percent(double ratio) {
    return String.format("%.1f%%", ratio * 100.0);
  }

  private static BufferedImage buildClosedPreview(
      Mat gray,
      boolean[][] flooded,
      boolean[][] interior,
      List<int[]> wallEndpoints,
      int leakX,
      int leakY
  ) {
    BufferedImage base = ImageProcessor.matToBufferedImage(gray);
    BufferedImage preview = ImageProcessor.toArgbOverlayCanvas(base);
    Graphics2D graphics = preview.createGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setComposite(AlphaComposite.SrcOver);

    if (flooded != null) {
      graphics.setColor(new Color(255, 60, 60, 170));
      for (int y = 0; y < flooded.length; y++) {
        for (int x = 0; x < flooded[y].length; x++) {
          if (flooded[y][x]) {
            graphics.fillRect(x, y, 1, 1);
          }
        }
      }
    }

    if (interior != null) {
      graphics.setColor(new Color(60, 140, 255, 160));
      for (int y = 0; y < interior.length; y++) {
        for (int x = 0; x < interior[y].length; x++) {
          if (interior[y][x]) {
            graphics.fillRect(x, y, 1, 1);
          }
        }
      }
    }

    if (wallEndpoints != null) {
      graphics.setColor(new Color(220, 60, 255));
      graphics.setStroke(new BasicStroke(3f));
      for (int[] endpoint : wallEndpoints) {
        int x = endpoint[0];
        int y = endpoint[1];
        graphics.drawOval(x - 6, y - 6, 12, 12);
        graphics.drawLine(x - 8, y, x + 8, y);
        graphics.drawLine(x, y - 8, x, y + 8);
      }
    }

    if (leakX >= 0 && leakY >= 0 && (wallEndpoints == null || wallEndpoints.isEmpty())) {
      graphics.setComposite(AlphaComposite.SrcOver);
      graphics.setColor(new Color(255, 0, 0));
      graphics.setStroke(new BasicStroke(4f));
      graphics.drawOval(leakX - 12, leakY - 12, 24, 24);
      graphics.drawLine(leakX - 18, leakY, leakX + 18, leakY);
      graphics.drawLine(leakX, leakY - 18, leakX, leakY + 18);
      graphics.setColor(new Color(255, 255, 0));
      graphics.drawString("(" + leakX + "," + leakY + ")", leakX + 16, leakY - 8);
    }

    graphics.dispose();
    return preview;
  }

  private static PathfindingMapClosureAnalysis failure(String message) {
    return new PathfindingMapClosureAnalysis(
        false, 0, 0, 0, false, -1, -1, null, null, message, null, 0, 0);
  }

  private record FloodOrder(boolean[][] flooded, List<int[]> processedPoints) {
  }
}
