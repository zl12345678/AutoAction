package com.auto.opencv.utils;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;

/**
 * 可视化器
 */
public class Visualizer {
    private Mat displayMat;

    public Visualizer(Mat originalImage) {
        this.displayMat = originalImage.clone();
    }

    /**
     * 绘制匹配区域矩形
     */
    public void drawMatchArea(Point center, Size templateSize) {
        Point topLeft = new Point(
                center.x - templateSize.width / 2,
                center.y - templateSize.height / 2
        );
        Scalar green = new Scalar(0, 255, 0);
        Imgproc.rectangle(displayMat,
                topLeft,
                new Point(topLeft.x + templateSize.width, topLeft.y + templateSize.height),
                green, 2);

        // 绘制中心点
        Imgproc.circle(displayMat, center, 8, green, -1);
    }

    /**
     * 绘制人物位置
     */
    public void drawCharacterPosition(Point position) {
        Imgproc.circle(displayMat, position, 5, new Scalar(0, 0, 255), -1);
    }

    /**
     * 绘制路径
     */
    public void drawPath(int[][] path) {
        for (int[] point : path) {
            Imgproc.circle(displayMat, new Point(point[0], point[1]), 1, new Scalar(0, 255, 0), -1);
        }
    }

    /**
     * 绘制起点和终点
     */
    public void drawStartAndEnd(Point start, Point end) {
        Imgproc.circle(displayMat, start, 5, new Scalar(0, 0, 255), -1);
        Imgproc.circle(displayMat, end, 5, new Scalar(255, 0, 0), -1);
    }

    /**
     * 显示结果图像
     */
    public void showResult() {
        HighGui.imshow("路径规划结果", displayMat);
        HighGui.waitKey(0);
        HighGui.destroyAllWindows();
    }
    /**
     * 显示结果图像
     */
    public void showResult(Mat displayMat) {
        HighGui.imshow("自定义显示结果", displayMat);
        HighGui.waitKey(0);
        HighGui.destroyAllWindows();
    }

    public void drawPoint(Point point, Scalar scalar) {
        Imgproc.circle(displayMat, point, 5, scalar, -1);
    }
}
