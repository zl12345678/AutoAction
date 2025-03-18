package com.auto;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class DebuggableClicker {
    private static Robot robot;

    static {
        try {
            robot = new Robot();
            robot.setAutoDelay(100);
        } catch (AWTException e) {
            throw new RuntimeException("Robot 初始化失败", e);
        }
    }

    public static void main(String[] args) throws NativeHookException {
        // 调试输出屏幕信息
        System.out.println("屏幕分辨率: " + Toolkit.getDefaultToolkit().getScreenSize());

        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
            @Override
            public void nativeKeyPressed(NativeKeyEvent e) {
                System.out.printf("[调试] 按键: %s, 修饰符: %s\n",
                        NativeKeyEvent.getKeyText(e.getKeyCode()),
                        NativeKeyEvent.getModifiersText(e.getModifiers())
                );

                if (e.getKeyCode() == NativeKeyEvent.VC_F1) {
                    clickAt(new Point(100, 100)); // 测试固定坐标
                }
                if (e.getKeyCode() == NativeKeyEvent.VC_F2) {
                    testKeyPress(KeyEvent.VK_A);
                }
            }

            @Override
            public void nativeKeyReleased(NativeKeyEvent e) {
            }

            @Override
            public void nativeKeyTyped(NativeKeyEvent e) {
            }
        });

        // 保持程序运行
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static void clickAt(Point pos) {
        try {
            System.out.println("尝试点击位置: " + pos);

            robot.mouseMove(pos.x, pos.y);
            robot.waitForIdle();

            // 验证实际位置
            Point actual = MouseInfo.getPointerInfo().getLocation();
            System.out.println("实际到达位置: " + actual);

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            System.out.println("点击完成");
        } catch (Exception e) {
            System.err.println("点击失败: " + e.getMessage());
        }
    }

    private static void testKeyPress(int key) {
        System.out.println("测试按下键: " + key);
        robot.keyPress(key);
        robot.keyRelease(key);
    }

}