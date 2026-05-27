package com.auto.vision;

import com.auto.opencv.utils.ImageProcessor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Row-by-row replay data for a top-down border flood, including per-row start/end span metrics.
 */
public final class PathfindingMapClosureFloodTrace {

    private static final int ROW_SPAN_TOLERANCE = 2;

    private final BufferedImage baseImage;
    private final int rows;
    private final int cols;
    private final int[] orderX;
    private final int[] orderY;
    private final int[] prefixCountByRow;
    private final int[] floodedMinXAtRow;
    private final int[] floodedMaxXAtRow;
    private final int[] walkableMinXAtRow;
    private final int[] walkableMaxXAtRow;
    private final boolean[][] interior;
    private final boolean borderWalkable;
    private final int globalStartX;
    private final int globalStartY;
    private final int globalEndX;
    private final int globalEndY;
    private final double globalDistance;
    private final int incompleteRowCount;

    public PathfindingMapClosureFloodTrace(
            BufferedImage baseImage,
            int rows,
            int cols,
            int[] orderX,
            int[] orderY,
            int[] prefixCountByRow,
            int[] walkableMinXAtRow,
            int[] walkableMaxXAtRow,
            boolean[][] interior,
            boolean borderWalkable
    ) {
        this.baseImage = ImageProcessor.toArgbOverlayCanvas(baseImage);
        this.rows = rows;
        this.cols = cols;
        this.orderX = orderX;
        this.orderY = orderY;
        this.prefixCountByRow = prefixCountByRow;
        this.walkableMinXAtRow = walkableMinXAtRow;
        this.walkableMaxXAtRow = walkableMaxXAtRow;
        this.interior = interior;
        this.borderWalkable = borderWalkable;
        this.floodedMinXAtRow = new int[rows];
        this.floodedMaxXAtRow = new int[rows];
        Arrays.fill(this.floodedMinXAtRow, -1);
        Arrays.fill(this.floodedMaxXAtRow, -1);
        buildFloodedRowBounds();
        if (orderX.length > 0) {
            this.globalStartX = orderX[0];
            this.globalStartY = orderY[0];
            this.globalEndX = orderX[orderX.length - 1];
            this.globalEndY = orderY[orderY.length - 1];
        } else {
            this.globalStartX = -1;
            this.globalStartY = -1;
            this.globalEndX = -1;
            this.globalEndY = -1;
        }
        this.globalDistance = distance(globalStartX, globalStartY, globalEndX, globalEndY);
        this.incompleteRowCount = countIncompleteRows();
    }

    public int rows() {
        return rows;
    }

    public int floodedCountAtRow(int row) {
        int clamped = Math.max(0, Math.min(row, rows - 1));
        return prefixCountByRow[clamped];
    }

    public int totalFlooded() {
        return orderX.length;
    }

    public boolean borderWalkable() {
        return borderWalkable;
    }

    public int globalStartX() {
        return globalStartX;
    }

    public int globalStartY() {
        return globalStartY;
    }

    public int globalEndX() {
        return globalEndX;
    }

    public int globalEndY() {
        return globalEndY;
    }

    public double globalDistance() {
        return globalDistance;
    }

    public int incompleteRowCount() {
        return incompleteRowCount;
    }

    public RowSpanInfo rowSpan(int row) {
        int clamped = Math.max(0, Math.min(row, rows - 1));
        int floodedStart = floodedMinXAtRow[clamped];
        int floodedEnd = floodedMaxXAtRow[clamped];
        int walkableStart = walkableMinXAtRow[clamped];
        int walkableEnd = walkableMaxXAtRow[clamped];
        int floodedSpan = span(floodedStart, floodedEnd);
        int walkableSpan = span(walkableStart, walkableEnd);
        return new RowSpanInfo(
                clamped,
                floodedStart,
                floodedEnd,
                floodedSpan,
                walkableStart,
                walkableEnd,
                walkableSpan,
                isRowSpanIncomplete(floodedSpan, walkableSpan)
        );
    }

    public BufferedImage render(int row) {
        int clampedRow = Math.max(0, Math.min(row, rows - 1));
        boolean[][] flooded = buildFloodedMask(clampedRow);
        boolean showInterior = clampedRow >= rows - 1;
        RowSpanInfo span = rowSpan(clampedRow);
        return renderFrame(baseImage, flooded, interior, clampedRow, showInterior, span);
    }

    public String globalSpanSummary() {
        if (globalStartX < 0) {
            return "泛洪序列为空";
        }
        return String.format(
                "泛洪首点(%d,%d) 尾点(%d,%d) 直线距离 %.1f px",
                globalStartX,
                globalStartY,
                globalEndX,
                globalEndY,
                globalDistance
        );
    }

    static int[] buildPrefixCountByRow(int rows, int[] orderY) {
        int[] prefixCountByRow = new int[rows];
        int index = 0;
        for (int row = 0; row < rows; row++) {
            while (index < orderY.length && orderY[index] <= row) {
                index++;
            }
            prefixCountByRow[row] = index;
        }
        return prefixCountByRow;
    }

    static int[] buildWalkableMinXAtRow(boolean[][] walkable, int rows, int cols) {
        int[] minXAtRow = new int[rows];
        Arrays.fill(minXAtRow, -1);
        for (int y = 0; y < rows; y++) {
            int minX = cols;
            int maxX = -1;
            for (int x = 0; x < cols; x++) {
                if (walkable[y][x]) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
            if (maxX >= 0) {
                minXAtRow[y] = minX;
            }
        }
        return minXAtRow;
    }

    static int[] buildWalkableMaxXAtRow(boolean[][] walkable, int rows, int cols) {
        int[] maxXAtRow = new int[rows];
        Arrays.fill(maxXAtRow, -1);
        for (int y = 0; y < rows; y++) {
            int minX = cols;
            int maxX = -1;
            for (int x = 0; x < cols; x++) {
                if (walkable[y][x]) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
            if (maxX >= 0) {
                maxXAtRow[y] = maxX;
            }
        }
        return maxXAtRow;
    }

    private void buildFloodedRowBounds() {
        for (int row = 0; row < rows; row++) {
            int end = prefixCountByRow[row];
            int minX = cols;
            int maxX = -1;
            for (int index = 0; index < end; index++) {
                if (orderY[index] == row) {
                    minX = Math.min(minX, orderX[index]);
                    maxX = Math.max(maxX, orderX[index]);
                }
            }
            if (maxX >= 0) {
                floodedMinXAtRow[row] = minX;
                floodedMaxXAtRow[row] = maxX;
            }
        }
    }

    private int countIncompleteRows() {
        int count = 0;
        for (int row = 0; row < rows; row++) {
            int floodedSpan = span(floodedMinXAtRow[row], floodedMaxXAtRow[row]);
            int walkableSpan = span(walkableMinXAtRow[row], walkableMaxXAtRow[row]);
            if (walkableSpan >= 8 && isRowSpanIncomplete(floodedSpan, walkableSpan)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isRowSpanIncomplete(int floodedSpan, int walkableSpan) {
        return walkableSpan > 0 && (floodedSpan < 0 || floodedSpan + ROW_SPAN_TOLERANCE < walkableSpan);
    }

    private static int span(int start, int end) {
        if (start < 0 || end < 0) {
            return -1;
        }
        return end - start;
    }

    private static double distance(int x1, int y1, int x2, int y2) {
        if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) {
            return -1;
        }
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private boolean[][] buildFloodedMask(int row) {
        boolean[][] flooded = new boolean[rows][cols];
        int end = prefixCountByRow[row];
        for (int index = 0; index < end; index++) {
            flooded[orderY[index]][orderX[index]] = true;
        }
        return flooded;
    }

    static BufferedImage renderFrame(
            BufferedImage baseImage,
            boolean[][] flooded,
            boolean[][] interior,
            int currentRow,
            boolean showInterior,
            RowSpanInfo rowSpan
    ) {
        BufferedImage preview = ImageProcessor.toArgbOverlayCanvas(baseImage);
        Graphics2D graphics = preview.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setComposite(AlphaComposite.SrcOver);

        if (showInterior && interior != null) {
            graphics.setColor(new Color(60, 140, 255, 160));
            for (int y = 0; y < interior.length; y++) {
                for (int x = 0; x < interior[y].length; x++) {
                    if (interior[y][x]) {
                        graphics.fillRect(x, y, 1, 1);
                    }
                }
            }
        }

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

        if (currentRow >= 0 && currentRow < baseImage.getHeight()) {
            graphics.setColor(new Color(255, 220, 0, 220));
            graphics.setStroke(new BasicStroke(2f));
            graphics.drawLine(0, currentRow, baseImage.getWidth() - 1, currentRow);

            if (rowSpan != null && rowSpan.walkableStartX() >= 0 && rowSpan.walkableEndX() >= 0) {
                graphics.setColor(new Color(180, 180, 180, 160));
                graphics.setStroke(new BasicStroke(1f));
                graphics.drawLine(rowSpan.walkableStartX(), currentRow, rowSpan.walkableEndX(), currentRow);
            }

            if (rowSpan != null && rowSpan.floodedStartX() >= 0 && rowSpan.floodedEndX() >= 0) {
                graphics.setColor(new Color(0, 255, 120));
                graphics.fillOval(rowSpan.floodedStartX() - 4, currentRow - 4, 8, 8);
                graphics.setColor(new Color(255, 120, 0));
                graphics.fillOval(rowSpan.floodedEndX() - 4, currentRow - 4, 8, 8);
                graphics.setColor(new Color(255, 255, 255));
                graphics.drawString(
                        "首" + rowSpan.floodedStartX() + " 尾" + rowSpan.floodedEndX()
                                + " Δ" + rowSpan.floodedSpan(),
                        Math.max(8, rowSpan.floodedStartX()),
                        Math.max(14, currentRow - 8)
                );
            }
        }

        graphics.dispose();
        return preview;
    }

    public record RowSpanInfo(
            int row,
            int floodedStartX,
            int floodedEndX,
            int floodedSpan,
            int walkableStartX,
            int walkableEndX,
            int walkableSpan,
            boolean incomplete
    ) {
        public String summary() {
            if (walkableStartX < 0) {
                return "行 " + row + "：无可走区";
            }
            if (floodedStartX < 0) {
                return "行 " + row + "：可走[" + walkableStartX + "," + walkableEndX + "] 泛洪未到达";
            }
            return "行 " + row + "：首(" + floodedStartX + "," + row + ") 尾(" + floodedEndX + "," + row
                    + ") 泛洪跨度 " + floodedSpan + " / 可走跨度 " + walkableSpan
                    + (incomplete ? "（未覆盖）" : "");
        }
    }
}
