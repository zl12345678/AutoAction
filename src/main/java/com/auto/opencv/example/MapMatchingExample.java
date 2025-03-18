package com.auto.opencv.example;

import com.auto.opencv.process.MapMatcher;
import com.auto.opencv.process.PathPlanner;
import com.auto.opencv.utils.GeometryUtils;
import com.auto.opencv.utils.Visualizer;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 地图匹配示例主类
 */
public class MapMatchingExample {
    public static void main(String[] args) {
        // 加载OpenCV本地库
        URL url = ClassLoader.getSystemResource("lib/opencv/opencv_java4110.dll");
        System.load(url.getPath());

        // 加载大地图和小地图图像
        Mat largeMap = Imgcodecs.imread("src/main/resources/img/yuantu.bmp", Imgcodecs.IMREAD_COLOR);
        Mat smallMap = Imgcodecs.imread("src/main/resources/img/muban.png", Imgcodecs.IMREAD_COLOR);

        // 对大地图和小地图进行二值化处理
        Imgproc.threshold(largeMap, largeMap, 127, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(smallMap, smallMap, 127, 255, Imgproc.THRESH_BINARY);

        // 创建地图匹配器
        MapMatcher mapMatcher = new MapMatcher(largeMap, smallMap);

        // 执行地图匹配
        Mat M = mapMatcher.match();
        if (M.empty()) {
            System.out.println("匹配失败，请调整参数或检查图像。");
            return;
        }

        // 计算小地图中心在大地图中的坐标-人物在大地图中的位置坐标
        Point characterPosition = mapMatcher.calculateCenter(M);
        if (characterPosition == null) {
            System.out.println("变换后的坐标数据无效。");
            return;
        }
        System.out.printf("人物坐标: (%.2f, %.2f)%n", characterPosition.x, characterPosition.y);

        // 创建地图可视化器
        Mat largeMapBy = Imgcodecs.imread("src/main/resources/img/yrcl.bmp", Imgcodecs.IMREAD_COLOR);
        Visualizer visualizer = new Visualizer(largeMapBy);

        // 绘制匹配区域和人物位置
        visualizer.drawMatchArea(characterPosition, new Size(smallMap.cols(), smallMap.rows()));
        visualizer.drawCharacterPosition(characterPosition);

        // 显示结果图像
//        visualizer.showResult();

        //路径规划
        // 设置起点和终点
        Point start = characterPosition;
        Point end = new Point(150, 60);
        // 创建路径规划器
        PathPlanner planner = new PathPlanner(largeMapBy, start, end, 200.0);
        int[][] path = planner.findPath();

        // 绘制路径、起点和终点
        visualizer.drawPath(path);
        visualizer.drawStartAndEnd(start, end);

        // 显示结果图像
//        visualizer.showResult();

        // 交叉点（临时终点）
        Point intersectionPoint = GeometryUtils.calculateRectIntersection(characterPosition, smallMap, path);
        // 绘制交叉点
        visualizer.drawPoint(intersectionPoint, new Scalar(0, 255, 255)); // 使用黄色绘制交叉点
        // 显示结果图像
        visualizer.showResult();

    }

}
