package com.auto.opencv.utils;


import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser;
import org.opencv.core.Point;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;

// 游戏窗口点击器
public class GameWindowClicker {
    private HWND hwnd;
    private int gameWindowWidth;
    private int gameWindowHeight;
    private int gameWindowX;
    private int gameWindowY;
    public GameWindowClicker(String windowTitle) {
        // 获取游戏窗口的句柄
        hwnd = User32.INSTANCE.FindWindow(null, windowTitle);
        if (hwnd == null) {
            throw new RuntimeException("未找到游戏窗口");
        }
        getGameWindowSize();
    }

    public int getGameWindowWidth() {
        return gameWindowWidth;
    }

    public void setGameWindowWidth(int gameWindowWidth) {
        this.gameWindowWidth = gameWindowWidth;
    }

    public int getGameWindowHeight() {
        return gameWindowHeight;
    }

    public void setGameWindowHeight(int gameWindowHeight) {
        this.gameWindowHeight = gameWindowHeight;
    }

    public int getGameWindowX() {
        return gameWindowX;
    }

    public void setGameWindowX(int gameWindowX) {
        this.gameWindowX = gameWindowX;
    }

    public int getGameWindowY() {
        return gameWindowY;
    }

    public void setGameWindowY(int gameWindowY) {
        this.gameWindowY = gameWindowY;
    }
    /**
     * 截图游戏窗口
     *
     * @param x      截图的 x 坐标
     * @param y      截图的 y 坐标
     * @param width  截图的宽度
     * @param height 截图的高度
     * @return 截图的 BufferedImage
     */
    public BufferedImage captureGameWindow(int x, int y, int width, int height) {
        try {
            Robot robot = new Robot();
            Rectangle captureRect = new Rectangle(x, y, width, height);
            return robot.createScreenCapture(captureRect);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 在游戏窗口中点击指定坐标
     *
     * @param gameX       游戏窗口中的 x 坐标
     * @param gameY       游戏窗口中的 y 坐标
     */
    public void clickInGameWindow(int gameX, int gameY) {
        try {
            // 将游戏窗口坐标转换为屏幕坐标
            POINT point = new POINT();
            point.x = gameX;
            point.y = gameY;
            // 使用 Robot 类移动鼠标并点击
            Robot robot = new Robot();
            robot.mouseMove(point.x, point.y); // 移动鼠标到指定位置
            System.out.println("✅ 已在屏幕位置 (" + point.x + ", " + point.y + ") 执行移动");
        } catch (Exception e) {
            throw new RuntimeException("点击失败", e);
        }
    }

    /**
     * 在游戏窗口中心按下鼠标左键
     */
    public void clickIn() {
        try {
            // 确保窗口激活
            User32.INSTANCE.SetForegroundWindow(hwnd);
            Thread.sleep(100); // 等待窗口激活
            int x = gameWindowWidth/2 + gameWindowX;
            int y = gameWindowHeight/2 + gameWindowY;
            Robot robot = new Robot();
            robot.mouseMove(x, y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); // 移动鼠标到指定位置
            System.out.println("✅ 已在屏幕位置 (" + x + ", " + y + ") 执行按下");
        } catch (Exception e) {
            throw new RuntimeException("点击失败", e);
        }
    }

    /**
     * 松开鼠标左键
     */
    public void clickOut() {
        try {
            // 确保窗口激活
            Thread.sleep(100); // 等待窗口激活
            int x = gameWindowWidth/2 + gameWindowX;
            int y = gameWindowHeight/2 + gameWindowY;
            Robot robot = new Robot();
            robot.mouseMove(x, y);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); // 移动鼠标到指定位置
            System.out.println("✅ 已在屏幕位置 (" + x + ", " + y + ") 执行松开");
        } catch (Exception e) {
            throw new RuntimeException("点击失败", e);
        }
    }
    /**
     * 获取游戏窗口大小和位置
     */
    public void getGameWindowSize() {
        try {
            // 获取窗口的实际大小（包括边框和标题栏）
            WinUser.RECT windowRect = new WinUser.RECT();
            User32.INSTANCE.GetWindowRect(hwnd, windowRect);

            // 计算宽度和高度
            int width = windowRect.right - windowRect.left;
            int height = windowRect.bottom - windowRect.top;
            gameWindowWidth = width;
            gameWindowHeight = height;
            gameWindowX = windowRect.left;
            gameWindowY = windowRect.top;
        } catch (Exception e) {
            throw new RuntimeException("获取窗口大小失败", e);
        }
    }
    /**
     * 将大地图上的坐标转换为游戏窗口中的坐标
     * 当前地图坐标
     * @param currentMapPoint   大地图上的当前坐标
     * @param targetMapPoint    大地图上的目标坐标
     * @param currentGamePoint  游戏窗口中的当前坐标
     * @param step              移动步数
     *
     * @return 游戏窗口中的坐标
     */
    public Point convertToGameWindow(Point currentMapPoint,Point targetMapPoint,Point currentGamePoint,int step) {
       double k = Math.atan2(targetMapPoint.y-currentMapPoint.y,targetMapPoint.x-currentMapPoint.x);
        Point point = new Point();
        point.x = currentGamePoint.x + step * Math.cos(k);
        point.y = currentGamePoint.y + step * Math.sin(k);
        return point;
    }


}
