package com.auto.opencv.utils;

import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * 几何工具类
 */
public class GeometryUtils {
    /**
     * 计算小地图矩形与规划路径的交点-临时终点
     *
     * @param characterPosition 人物在大地图中的坐标（起点）
     * @param smallMap          小地图
     * @param path              规划路径
     * @return 交点（临时终点）
     */
    public static Point calculateRectIntersection(Point characterPosition, Mat smallMap, int[][] path) {
        // 计算小地图的矩形边
        Point rectTopLeft = new Point(characterPosition.x - smallMap.cols() / 2.0, characterPosition.y - smallMap.rows() / 2.0);
        Point rectTopRight = new Point(characterPosition.x + smallMap.cols() / 2.0, characterPosition.y - smallMap.rows() / 2.0);
        Point rectBottomLeft = new Point(characterPosition.x - smallMap.cols() / 2.0, characterPosition.y + smallMap.rows() / 2.0);
        Point rectBottomRight = new Point(characterPosition.x + smallMap.cols() / 2.0, characterPosition.y + smallMap.rows() / 2.0);

        // 小地图的矩形边
        Point[][] rectEdges = {{rectTopLeft, rectTopRight},    // 上边
                {rectTopRight, rectBottomRight}, // 右边
                {rectBottomRight, rectBottomLeft}, // 下边
                {rectBottomLeft, rectTopLeft}   // 左边
        };

        // 规划路径的线段
        List<Point[]> pathSegments = new ArrayList<>();
        for (int i = 0; i < path.length - 1; i++) {
            Point p1 = new Point(path[i][0], path[i][1]);
            Point p2 = new Point(path[i + 1][0], path[i + 1][1]);
            pathSegments.add(new Point[]{p1, p2});
        }

        // 计算交叉点
        List<Point> intersectionPoints = new ArrayList<>();
        for (Point[] rectEdge : rectEdges) {
            for (Point[] pathSegment : pathSegments) {
                Point intersection = calculateLineIntersection(rectEdge[0], rectEdge[1], pathSegment[0], pathSegment[1]);
                if (intersection != null) {
                    intersectionPoints.add(intersection);
                }
            }
        }

        // 输出交点坐标
        for (Point point : intersectionPoints) {
            System.out.printf("交叉点坐标: (%.2f, %.2f)%n", point.x, point.y);
        }
        return intersectionPoints.get(0);
    }

    /**
     * 计算两条线段的交叉点
     *
     * @param p1 线段1的起点
     * @param p2 线段1的终点
     * @param p3 线段2的起点
     * @param p4 线段2的终点
     * @return 交叉点，如果不存在则返回 null
     */
    public static Point calculateLineIntersection(Point p1, Point p2, Point p3, Point p4) {
        double x1 = p1.x, y1 = p1.y;
        double x2 = p2.x, y2 = p2.y;
        double x3 = p3.x, y3 = p3.y;
        double x4 = p4.x, y4 = p4.y;

        double denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (denominator == 0) {
            return null; // 平行或重合
        }

        double x = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denominator;
        double y = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denominator;

        // 检查交叉点是否在线段上
        if (x < Math.min(x1, x2) || x > Math.max(x1, x2) || y < Math.min(y1, y2) || y > Math.max(y1, y2)) {
            return null;
        }
        if (x < Math.min(x3, x4) || x > Math.max(x3, x4) || y < Math.min(y3, y4) || y > Math.max(y3, y4)) {
            return null;
        }

        return new Point(x, y);
    }
}
