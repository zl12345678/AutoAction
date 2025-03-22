package com.auto.opencv.utils;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import java.awt.*;

/**
 * 图像可视化器
 */
public class Visualizer {
    private Mat displayMat;
    private final String windowName;
    public Visualizer(Mat image, String windowName) {
        this.displayMat = image.clone();
        this.windowName = windowName;
        HighGui.namedWindow(windowName, HighGui.WINDOW_AUTOSIZE);
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
        HighGui.imshow(windowName, displayMat);
        HighGui.waitKey(1);
//        HighGui.destroyAllWindows();
    }

    /**
     *
     * @param winName 窗口名
     * @param displayMat 显示图像
     */
    public void showResult(String winName,Mat displayMat) {
        HighGui.imshow(winName, displayMat);
    }

    public void drawPoint(Point point, Scalar scalar) {
        Imgproc.circle(displayMat, point, 5, scalar, -1);
    }
    public void updateDisplayMat(Mat newDisplayMat) {
        this.displayMat = newDisplayMat;
    }
    public void close() {
        HighGui.destroyWindow(windowName);
    }
}
