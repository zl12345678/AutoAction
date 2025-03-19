package com.auto.opencv.utils;


import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser;
import org.opencv.core.Point;

import java.awt.*;
import java.awt.event.InputEvent;

// 游戏窗口点击器
public class GameWindowClicker {
    private HWND hwnd;
    public GameWindowClicker(String windowTitle) {
        // 获取游戏窗口的句柄
        hwnd = User32.INSTANCE.FindWindow(null, windowTitle);
        if (hwnd == null) {
            throw new RuntimeException("未找到游戏窗口");
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
            clientToScreen(hwnd, point);

            // 使用 Robot 点击
            Robot robot = new Robot();
            robot.mouseMove(point.x, point.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            System.out.println("✅ 已在屏幕位置 (" + point.x + ", " + point.y + ") 执行点击");
        } catch (Exception e) {
            throw new RuntimeException("点击失败", e);
        }
    }

    /**
     * 获取游戏窗口的宽度和高度
     *
     * @return 包含宽度和高度的数组 [width, height]
     */
    public int[] getGameWindowSize() {
        try {
            // 获取窗口的实际大小（包括边框和标题栏）
            WinUser.RECT windowRect = new WinUser.RECT();
            User32.INSTANCE.GetWindowRect(hwnd, windowRect);

            // 计算宽度和高度
            int width = windowRect.right - windowRect.left;
            int height = windowRect.bottom - windowRect.top;

            System.out.println("窗口宽度为：" + width);
            System.out.println("窗口高度为：" + height);

            // 返回宽度和高度
            return new int[]{width, height};
        } catch (Exception e) {
            throw new RuntimeException("获取窗口大小失败", e);
        }
    }
    /**
     * Fixme:将大地图上的坐标转换为游戏窗口中的坐标(未测试)
     *
     * @param mapPoint        大地图上的坐标
     * @param largeMapWidth    大地图的宽度
     * @param largeMapHeight   大地图的高度
     * @return 游戏窗口中的坐标
     */
    public Point convertToGameWindow(Point mapPoint, int largeMapWidth, int largeMapHeight) {
        int[] gameWindowSize = getGameWindowSize();
        int gameWindowWidth = gameWindowSize[0];
        int gameWindowHeight = gameWindowSize[1];
        // 计算比例
        double scaleX = (double) gameWindowWidth / largeMapWidth;
        double scaleY = (double) gameWindowHeight / largeMapHeight;

        // 转换为游戏窗口中的坐标
        int gameX = (int) (mapPoint.x * scaleX);
        int gameY = (int) (mapPoint.y * scaleY);

        return new Point(gameX, gameY);
    }
    /**
     * 将游戏坐标转换为屏幕坐标
     *
     * @param hwnd  窗口句柄
     * @param point 游戏坐标
     */
    private void clientToScreen(HWND hwnd, POINT point) {
        // 获取窗口在屏幕中的位置
        WinUser.RECT clientRect = new WinUser.RECT();
        User32.INSTANCE.GetWindowRect(hwnd, clientRect);
        // 计算屏幕坐标
        point.x = clientRect.left + point.x;
        point.y = clientRect.top + point.y;
    }
}
