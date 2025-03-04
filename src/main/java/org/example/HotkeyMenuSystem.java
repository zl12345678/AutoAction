package org.example;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import org.example.entity.Action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.AutoClicker.getNativeKeyCode;

/**
 * 热键菜单系统
 */
public class HotkeyMenuSystem {

    // 热键配置容器：修饰键掩码 + 键码 → 回调函数
    private static final Map<HotkeyConfig, Runnable> hotkeyActions = new HashMap<>();

    // 热键配置记录
    private record HotkeyConfig(int modifiers, int keyCode) {
    }

    static {
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(new HotkeyListener());
            System.out.println("🔥 热键系统已初始化");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // 释放 JNativeHook 或其他资源
                try {
                    GlobalScreen.unregisterNativeHook();
                } catch (Exception e) {
                    System.err.println("无法禁用 JNativeHook: " + e.getMessage());
                }
            }));

        } catch (Exception e) {
            throw new RuntimeException("热键系统初始化失败", e);
        }
    }

    /*public static void main(String[] args) throws NativeHookException {
        // 初始化全局监听
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(new HotkeyListener());

        // 示例：添加 Ctrl+F1 绑定
        addHotkey(
            NativeKeyEvent.CTRL_L_MASK,
            NativeKeyEvent.VC_F1,
            () -> System.out.println("📦 打开功能菜单")
        );

        // 示例：添加 Alt+Shift+S 绑定
        addHotkey(
            NativeKeyEvent.ALT_L_MASK | NativeKeyEvent.SHIFT_L_MASK,
            NativeKeyEvent.VC_S,
            () -> System.out.println("💾 执行快速保存")
        );
    }

    *//**
     * 添加热键绑定
     * @param modifiers 修饰键掩码组合
     * @param keyCode 目标键码
     * @param action 触发动作
     *//*
    public static void addHotkey(int modifiers, int keyCode, Runnable action) {
        System.out.printf("[注册热键] 修饰符: %04X, 键码: %s%n",
                modifiers, NativeKeyEvent.getKeyText(keyCode));
        hotkeyActions.put(new HotkeyConfig(modifiers, keyCode), action);
    }*/

    /**
     * 添加热键绑定（支持左右 CTRL 键）
     *
     * @param leftModifiers  左 CTRL 修饰键掩码
     * @param rightModifiers 右 CTRL 修饰键掩码
     * @param keyCode        目标键码
     * @param action         触发动作
     */
    public static void addHotkey(int leftModifiers, int rightModifiers, int keyCode, Runnable action) {
        if (rightModifiers == 0) {
            addHotkeyInternal(leftModifiers, keyCode, action);
        } else {
            addHotkeyInternal(leftModifiers, keyCode, action);
            addHotkeyInternal(rightModifiers, keyCode, action);
        }
    }

    /**
     * 添加热键绑定（仅支持功能键 F1-F12）
     *
     * @param keyCode 目标键码（如 NativeKeyEvent.VC_F1）
     * @param action  触发动作
     */
    public static void addHotkey(int keyCode, Runnable action) {
        if (keyCode < NativeKeyEvent.VC_F1 || keyCode > NativeKeyEvent.VC_F12) {
            System.out.printf("[警告] 键码 %s 不在 F1-F12 范围内%n", NativeKeyEvent.getKeyText(keyCode));
            return;
        }
        HotkeyConfig config = new HotkeyConfig(0, keyCode); // 修饰键为 0
        if (hotkeyActions.containsKey(config)) {
            System.out.printf("[警告] 热键已存在: 键码: %s%n", NativeKeyEvent.getKeyText(keyCode));
            return;
        }
        hotkeyActions.put(config, action);
        System.out.printf("[注册热键] 键码: %s%n", NativeKeyEvent.getKeyText(keyCode));
    }

    private static void addHotkeyInternal(int modifiers, int keyCode, Runnable action) {
        HotkeyConfig config = new HotkeyConfig(modifiers, keyCode);
        if (hotkeyActions.containsKey(config)) {
            System.out.printf("[警告] 热键已存在: 修饰符: %04X, 键码: %s%n", modifiers, NativeKeyEvent.getKeyText(keyCode));
            return;
        }
        hotkeyActions.put(config, action);
        System.out.printf("[注册热键] 修饰符: %04X, 键码: %s%n", modifiers, NativeKeyEvent.getKeyText(keyCode));
    }

    /**
     * 移除热键绑定
     */
    public static void removeHotkey(int modifiers, int keyCode) {
        hotkeyActions.remove(new HotkeyConfig(modifiers, keyCode));
    }

    /**
     * 移除热键绑定（仅支持功能键 F1-F12）
     *
     * @param keyCode 目标键码（如 NativeKeyEvent.VC_F1）
     */
    public static void removeHotkey(int keyCode) {
        if (keyCode < NativeKeyEvent.VC_F1 || keyCode > NativeKeyEvent.VC_F12) {
            System.out.printf("[警告] 键码 %s 不在 F1-F12 范围内%n", NativeKeyEvent.getKeyText(keyCode));
            return;
        }
        HotkeyConfig config = new HotkeyConfig(0, keyCode); // 修饰键为 0
        hotkeyActions.remove(config);
    }

    /**
     * 热键监听器实现
     */
    private static class HotkeyListener implements NativeKeyListener {
        @Override
        public void nativeKeyPressed(NativeKeyEvent e) {
            // 遍历所有注册的热键
            hotkeyActions.forEach((config, action) -> {
                // 精确匹配修饰键和键码
                if ((e.getModifiers() & config.modifiers) == config.modifiers
                        && e.getKeyCode() == config.keyCode) {
                    action.run();
                }
            });
        }

        @Override
        public void nativeKeyReleased(NativeKeyEvent e) {
        }

        @Override
        public void nativeKeyTyped(NativeKeyEvent e) {
        }
    }

    /**
     * 显示菜单
     *
     * @param actions 行为对象列表
     */
    public static void displayMenu(List<Action> actions) {
        System.out.println("🔥 热键菜单 🔥");
        System.out.println("热键: F1, 描述: 显示菜单");
        System.out.println("热键: F2, 描述: 绑定窗口");
        // TODO:接入OpenCV实现识图画图
        System.out.println("热键: F3, 描述: 接入OpenCV实现识图画图（暂未实现）");
        // TODO:重新加载自定义配置(JSON文件)
        System.out.println("热键: F4, 描述: 重新加载自定义配置（暂未实现）");
        for (Action action : actions) {
            String hotkeyText = NativeKeyEvent.getKeyText(getNativeKeyCode(action.getHotkey()));
            String description = action.getDescription();
            System.out.printf("热键: %s, 描述: %s%n", hotkeyText, description);
        }
        System.out.println("热键: Ctrl+Esc, 描述: 停止所有动作");
    }
}