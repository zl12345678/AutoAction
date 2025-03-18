package com.auto.opencv.example;

import com.auto.opencv.process.PathPlanner;
import com.auto.opencv.utils.Visualizer;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;

import java.net.URL;

/**
 * 路径规划示例主类
 */
public class PathPlanningExample {
    public static void main(String[] args) {
        // 加载OpenCV本地库
        URL url = ClassLoader.getSystemResource("lib/opencv/opencv_java4110.dll");
        System.load(url.getPath());

        // 加载图像
        Mat image = Imgcodecs.imread("src/main/resources/img/yrcl.bmp", Imgcodecs.IMREAD_COLOR);
        if (image.empty()) {
            System.out.println("Could not open or find the image");
            return;
        }

        // 设置起点和终点
        Point start = new Point(30, 465);
        Point end = new Point(150, 60);

        // 创建路径规划器
        PathPlanner planner = new PathPlanner(image, start, end, 200.0);

        // 执行路径规划
        int[][] path = planner.findPath();

        // 创建路径可视化器
        Visualizer visualizer = new Visualizer(image);

        // 绘制路径、起点和终点
        visualizer.drawPath(path);
        visualizer.drawStartAndEnd(start, end);

        // 显示结果图像
        visualizer.showResult();
    }
}
