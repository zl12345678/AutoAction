package com.auto.opencv.example;

import com.auto.AutoClicker;
import com.auto.opencv.process.MapMatcher;
import com.auto.opencv.process.PathPlanner;
import com.auto.opencv.utils.GameWindowClicker;
import com.auto.opencv.utils.GeometryUtils;
import com.auto.opencv.utils.Visualizer;
import org.junit.Test;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.*;
import java.awt.event.InputEvent;
import java.net.URL;

/**
 * 路径规划示例主类
 */
public class ProcessTest {
    static {
        // 加载OpenCV本地库
        URL url = ClassLoader.getSystemResource("lib/opencv/opencv_java4110.dll");
        System.load(url.getPath());
    }

    // 路径规划示例
    @Test
    public void pathPlanning() {
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

    // 地图匹配示例
    @Test
    public void MapMatching() {
        // 加载大地图和小地图图像
        Mat largeMap = Imgcodecs.imread("src/main/resources/img/yuantu.bmp", Imgcodecs.IMREAD_COLOR);
        Mat smallMap = Imgcodecs.imread("src/main/resources/img/muban.png", Imgcodecs.IMREAD_COLOR);

        // 对大地图和小地图进行二值化处理
//        Imgproc.threshold(largeMap, largeMap, 127, 255, Imgproc.THRESH_BINARY);
//        Imgproc.threshold(smallMap, smallMap, 127, 255, Imgproc.THRESH_BINARY);

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
        visualizer.showResult();

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

        // 交点（临时终点）
        Point intersectionPoint = GeometryUtils.calculateRectIntersection(characterPosition, smallMap, path);
        // 绘制交点
        visualizer.drawPoint(intersectionPoint, new Scalar(0, 255, 255)); // 使用黄色绘制交点
        // 显示结果图像
        visualizer.showResult();

    }

    /**
     * 无坐标游戏寻路流程示例
     * 1. 地图匹配-获取当前人物位置
     * 2. 完整路径规划-获取完整规划路径
     * 3. 当前视野路径规划-坐标转换-点击坐标
     */
    @Test
    public void torchlightFlow() {
        //1. ✅ 地图匹配-获取当前人物位置
        // 加载大地图和小地图图像
        // 实际应为游戏中地图截图
        Mat largeMap = Imgcodecs.imread("src/main/resources/img/yuantu.bmp", Imgcodecs.IMREAD_COLOR);
        Mat smallMap = Imgcodecs.imread("src/main/resources/img/muban.png", Imgcodecs.IMREAD_COLOR);

        // 对大地图和小地图进行二值化处理
        // 地图匹配是否进行二值化？
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
        // 提取缩放系数 大地图与小地图的缩放比例
        double scale = mapMatcher.calculateScale(M);
        System.out.printf("大地图与小地图的缩放比例为%f", scale);
        // 计算小地图中心在大地图中的坐标-人物在大地图中的位置坐标
        Point characterPosition = mapMatcher.calculateCenter(M);
        if (characterPosition == null) {
            System.out.println("变换后的坐标数据无效。");
            return;
        }
        System.out.printf("人物坐标: (%.2f, %.2f)%n", characterPosition.x, characterPosition.y);

        // 此图为手动绘制的二值化地图
        Mat binaryMap = Imgcodecs.imread("src/main/resources/img/yrcl.bmp", Imgcodecs.IMREAD_COLOR);
        // 创建地图可视化器
        Visualizer visualizer = new Visualizer(binaryMap);

        // 绘制匹配区域和人物位置
        visualizer.drawMatchArea(characterPosition, new Size(smallMap.cols(), smallMap.rows()));
        visualizer.drawCharacterPosition(characterPosition);

        // 显示结果图像
//        visualizer.showResult();

        //2.✅  完整路径规划-获取完整规划路径
        // 设置起点和终点
        Point start = characterPosition;
        Point end = new Point(150, 60);
        // 创建路径规划器
        PathPlanner planner = new PathPlanner(binaryMap, start, end, 200.0);
        int[][] path = planner.findPath();

        // 绘制路径、起点和终点
        visualizer.drawPath(path);
        visualizer.drawStartAndEnd(start, end);

        // 显示结果图像
//        visualizer.showResult();

        //3. ✅当前视野路径规划-获取下一步点击位置
        // 交点（临时终点）
        Point intersectionPoint = GeometryUtils.calculateRectIntersection(characterPosition, smallMap, path);
        // 绘制交点
//        visualizer.drawPoint(intersectionPoint, new Scalar(0, 255, 255)); // 使用黄色绘制交点
        // 显示结果图像
//        visualizer.showResult();


        // 游戏相关
        // 此处测试用，使用1.txt的文本窗口中心作为测试点击点
        GameWindowClicker gameWindowClicker = new GameWindowClicker("1.txt - Notepad");
//        坐标转换为游戏坐标
        Point point = gameWindowClicker.convertToGameWindow(intersectionPoint, largeMap.cols(), largeMap.rows());

//        测试
        int[] gameWindowSize = gameWindowClicker.getGameWindowSize();
        point.x = (double) gameWindowSize[0] / 2;
        point.y = (double) gameWindowSize[1] / 2;

        System.out.println("转换后的游戏坐标为：" + point);
//        游戏坐标转换为屏幕坐标并点击
        gameWindowClicker.clickInGameWindow((int) point.x, (int) point.y);

    }

}
