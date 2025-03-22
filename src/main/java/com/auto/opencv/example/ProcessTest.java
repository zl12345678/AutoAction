package com.auto.opencv.example;

import com.auto.opencv.process.MapMatcher;
import com.auto.opencv.process.PathPlanner;
import com.auto.opencv.utils.GameWindowClicker;
import com.auto.opencv.utils.Visualizer;
import org.junit.Test;
import org.opencv.core.*;

import java.awt.image.BufferedImage;
import java.net.URL;

import static com.auto.opencv.process.ArrowMatcher.matchArrow;
import static com.auto.opencv.utils.ImageProcessor.*;
import static java.lang.Thread.sleep;

/**
 * 路径规划示例主类 - 以 曙光大道 地图测试
 */
public class ProcessTest {
    static {
        // 加载OpenCV本地库
        URL url = ClassLoader.getSystemResource("lib/opencv/opencv_java4110.dll");
        System.load(url.getPath());
    }

//    地图匹配及规划路线可视化
    @Test
    public void mapMatchTest() {
        // 加载大地图和游戏窗口截图

        Mat arrowTemplate = loadImage("src/main/resources/img/arrow_template2.bmp");
        // 创建 GameWindowClicker 实例
        Visualizer visualizer = new Visualizer(new Mat(), "bbb");
        while (true) {
            try {
                Mat largeMap = loadImage("src/main/resources/img/sggd/largeMap_2.bmp");
                Mat largeMapClone = largeMap.clone();
                // 创建 GameWindowClicker 实例
                GameWindowClicker gameWindowClicker = new GameWindowClicker("Torchlight: Infinite  ");

                // 捕获游戏窗口截图
                BufferedImage gameWindowImage = gameWindowClicker.captureGameWindow(gameWindowClicker.getGameWindowX(),
                        gameWindowClicker.getGameWindowY(),
                        gameWindowClicker.getGameWindowWidth(),
                        gameWindowClicker.getGameWindowHeight());
                if (gameWindowImage == null) {
                    throw new RuntimeException("无法捕获游戏窗口截图");
                }

                // 将 BufferedImage 转换为 OpenCV 的 Mat
                Mat gameWindow = bufferedImageToMat(gameWindowImage);

                // 截取小地图区域
                Mat smallMap = extractRegion(gameWindow, new Rect(0, 100, 200, 200));

                // 匹配人物箭头
                Point arrowCenter = matchArrow(smallMap, arrowTemplate);

                // 截取箭头周围的匹配区域
                Mat matchArea = extractCenterArea(smallMap, arrowCenter, 100);

                // 匹配大地图并计算人物位置
                Point characterPosition = matchLargeMap(largeMap, matchArea);

                // 设置起点和终点
                Point start = characterPosition;
                Point end = new Point(140, 110);
                // 路径规划
                int[][] path = planPath(largeMap, start, end, 200.0);
//                visualizer.updateDisplayMat(largeMap);
                // 绘制路径、起点和终点
                visualizer.updateDisplayMat(largeMapClone);
                visualizer.drawMatchArea(characterPosition, new Size(matchArea.cols(), matchArea.rows()));
                visualizer.drawCharacterPosition(characterPosition);
                visualizer.drawPath(path);
                visualizer.drawStartAndEnd(start, end);
                visualizer.showResult();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //    在小地图上定位人物箭头
    @Test
    public void locatingArrow() {
        while (true) {
            GameWindowClicker gameWindowClicker = new GameWindowClicker("Torchlight: Infinite  ");

            // 捕获游戏窗口截图
            BufferedImage gameWindowImage = gameWindowClicker.captureGameWindow(gameWindowClicker.getGameWindowX(),
                    gameWindowClicker.getGameWindowY(),
                    gameWindowClicker.getGameWindowWidth(),
                    gameWindowClicker.getGameWindowHeight());
            if (gameWindowImage == null) {
                throw new RuntimeException("无法捕获游戏窗口截图");
            }

            // 将 BufferedImage 转换为 OpenCV 的 Mat
            Mat gameWindow = bufferedImageToMat(gameWindowImage);

            // 截取小地图区域
            Mat smallMap = extractRegion(gameWindow, new Rect(0, 100, 200, 200));
            Mat arrowTemplate = loadImage("src/main/resources/img/arrow_template2.bmp");
            // 匹配人物箭头
            Point arrowCenter = matchArrow(smallMap, arrowTemplate);
            Visualizer visualizer = new Visualizer(smallMap, "小地图位置");
            visualizer.drawPoint(arrowCenter, new Scalar(0, 255, 0));
            visualizer.showResult();
        }

    }

    //    地图匹配-定位人物位置
    @Test
    public void locatingPlayer() throws InterruptedException {
        Mat largeMap = loadImage("src/main/resources/img/sggd/largeMap_2.bmp");
        Visualizer visualizer = new Visualizer(largeMap, "das");
        sleep(2000);
        while (true) {
            Mat largeMapClone = largeMap.clone();
            Point characterPosition = getMapCurrentPoint(largeMap);
            visualizer.updateDisplayMat(largeMapClone);
            visualizer.drawMatchArea(characterPosition, new Size(100, 100));
            visualizer.drawCharacterPosition(characterPosition);
            visualizer.showResult();
        }
    }

    /**
     * 获取当前在大地图的坐标
     */
    public Point getMapCurrentPoint(Mat largeMap) {
//        Visualizer visualizer = new Visualizer(largeMap, "aaa");
        // 创建 GameWindowClicker 实例
        GameWindowClicker gameWindowClicker = new GameWindowClicker("Torchlight: Infinite  ");

        // 捕获小地图
        BufferedImage gameWindowImage = gameWindowClicker.captureGameWindow(gameWindowClicker.getGameWindowX(),
                gameWindowClicker.getGameWindowY()+100,
                200,
                200);
        if (gameWindowImage == null) {
            throw new RuntimeException("无法捕获游戏窗口截图");
        }

        // 将 BufferedImage 转换为 OpenCV 的 Mat
        Mat smallMap = bufferedImageToMat(gameWindowImage);

        // 截取小地图区域
        Mat arrowTemplate = loadImage("src/main/resources/img/arrow_template2.bmp");
        // 匹配人物箭头
        Point arrowCenter = matchArrow(smallMap, arrowTemplate);

        // 截取箭头周围的匹配区域
        Mat matchArea = extractCenterArea(smallMap, arrowCenter, 100);
        // 匹配大地图并计算人物位置
        Point characterPosition = matchLargeMap(largeMap, matchArea);
        return new Point((int) characterPosition.x, (int) characterPosition.y);
    }

    /**
     * 寻路测试
     */
    @Test
    public void wayFindingTest() {
        // 加载大地图和游戏窗口截图
        Mat largeMap = loadImage("src/main/resources/img/sggd/largeMap_2.bmp");
        Point characterPosition = getMapCurrentPoint(largeMap);
        GameWindowClicker gameWindowClicker = new GameWindowClicker("Torchlight: Infinite  ");

        // 设置起点和终点
        Point start = characterPosition;
        Point end = new Point(140, 110);
        // 路径规划
        int[][] path = planPath(largeMap, start, end, 200.0);

        // 在游戏中点击路径点，使人物移动
        gameWindowClicker.clickIn();
        for (int[] point : path) {
//        for (int i = 0; i < 700; i++) {
//            int[] point = path[i];
            int x = gameWindowClicker.getGameWindowWidth() / 2 + gameWindowClicker.getGameWindowX();
            int y = gameWindowClicker.getGameWindowHeight() / 2 + gameWindowClicker.getGameWindowY();
            Point currentPoint = new Point(x, y);
            try {
                currentPoint = getMapCurrentPoint(largeMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Point targetPoint = new Point(point[0], point[1]);

            if (getDistance(targetPoint, currentPoint) > 200) {
                System.out.println("错误判断：currentPoint:" + currentPoint.x + "," + currentPoint.y + " -->" + targetPoint.x + "," + targetPoint.y);
                continue;
            }
            if (getDistance(targetPoint, end) < 5) {
                System.out.println("已到达终点附近");
                gameWindowClicker.clickOut();
                break;
            }
            // 将地图坐标转换为游戏窗口坐标
            Point gamePoint = gameWindowClicker.convertToGameWindow(currentPoint, targetPoint, new Point(x, y), 80);
            System.out.println("正确判断1：currentPoint:" + currentPoint.x + "," + currentPoint.y + " -->" + targetPoint.x + "," + targetPoint.y);
            System.out.println("正确判断2：gameWindow:" + x + "," + y + " -->" + gamePoint.x + "," + gamePoint.y);
            // 点击游戏窗口中的坐标
            gameWindowClicker.clickInGameWindow((int) gamePoint.x, (int) gamePoint.y);
            while (true) {
                currentPoint = getMapCurrentPoint(largeMap);
                // 将地图坐标转换为游戏窗口坐标
                gamePoint = gameWindowClicker.convertToGameWindow(currentPoint, targetPoint, new Point(x, y), 80);
                // 点击游戏窗口中的坐标
                gameWindowClicker.clickInGameWindow((int) gamePoint.x, (int) gamePoint.y);
                System.out.println("正确判断1：currentPoint:" + currentPoint.x + "," + currentPoint.y + " -->" + targetPoint.x + "," + targetPoint.y);
                System.out.println("正确判断2：gameWindow:" + x + "," + y + " -->" + gamePoint.x + "," + gamePoint.y);
                if (getDistance(targetPoint, currentPoint) < 10) {
                    System.out.println("已到达目标点附近");
                    break;
                }
            }
        }
    }


    // 匹配大地图并计算人物位置
    private Point matchLargeMap(Mat largeMap, Mat matchArea) {
        MapMatcher mapMatcher = new MapMatcher(largeMap, matchArea);
        Mat M = mapMatcher.match();
        return mapMatcher.calculateCenter(M);
    }

    // 路径规划
    private int[][] planPath(Mat map, Point start, Point end, double obstacleThreshold) {
        PathPlanner planner = new PathPlanner(map, start, end, obstacleThreshold);
        return planner.findPath();
    }
}
