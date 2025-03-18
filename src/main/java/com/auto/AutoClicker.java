package com.auto;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.auto.entity.Action;
import com.auto.entity.Behavior;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


/**
 * 自动点击器
 */
public class AutoClicker {
    private static final Robot robot;
    private static final List<Action> actionsList = new ArrayList<>();
    private static volatile boolean isRunning = true; // 添加标志变量
    private static final String CONFIG_FILE_PATH = "clickConfig.json"; // 定义常量
    // 绑定PID
    static long boundProcessId = 0;

    static {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("初始化失败", e);
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        // 强制加载 HotkeyMenuSystem 类（触发静态块）
        Class.forName("com.auto.HotkeyMenuSystem");

        // 添加停止所有点击的热键绑定
        HotkeyMenuSystem.addHotkey(
                NativeKeyEvent.CTRL_L_MASK,
                NativeKeyEvent.CTRL_R_MASK,
                NativeKeyEvent.VC_ESCAPE,
                AutoClicker::stopAllClicks
        );
        // 添加 F1 绑定来显示菜单
        HotkeyMenuSystem.addHotkey(
                NativeKeyEvent.VC_F1,
                () -> HotkeyMenuSystem.displayMenu(actionsList)
        );
        // 添加 F2 绑定，用于绑定当前活动窗口
        HotkeyMenuSystem.addHotkey(
                NativeKeyEvent.VC_F2,
                () -> {
                    boundProcessId = getActiveWindowProcessId();
                    System.out.println("📦 当前窗口已绑定," + boundProcessId);
                });
        // 添加 F3绑定，接入OpenCV实现识图画图（暂未实现）
        HotkeyMenuSystem.addHotkey(
                NativeKeyEvent.VC_F3,
                () -> System.out.println("📦 接入OpenCV实现识图画图（暂未实现）")
        );
        // 添加 F4 绑定，用于重新加载自定义配置（暂未实现）
        HotkeyMenuSystem.addHotkey(
                NativeKeyEvent.VC_F4,
                () -> System.out.println("📦 重新加载自定义配置（暂未实现）")
        );

        // 读取 JSON 文件并初始化配置

        try {
            initializeFromJson();
        } catch (IOException e) {
            System.out.println("请检查clickConfig.json文件");
            throw new RuntimeException(e);
        }

        // 初始显示菜单
        HotkeyMenuSystem.displayMenu(actionsList);
    }

    /**
     * 从 JSON 文件初始化配置
     *
     * @throws IOException 读取文件时发生错误
     */
    private static void initializeFromJson() throws IOException {
        InputStream inputStream = AutoClicker.class.getClassLoader().getResourceAsStream(CONFIG_FILE_PATH);
        if (inputStream == null) {
            throw new IOException("文件未找到: " + CONFIG_FILE_PATH);
        }
        // 使用 UTF-8 编码读取文件内容
        try (Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("\\A")) {
            if (!scanner.hasNext()) {
                throw new IOException("文件为空");
            }
            String jsonContent = scanner.next();
            JSONObject jsonObject = new JSONObject(jsonContent);
            JSONArray clicks = jsonObject.getJSONArray("clicks");

            for (int i = 0; i < clicks.length(); i++) {
                JSONObject click = clicks.getJSONObject(i);
                String name = click.getString("name");
                String description = click.getString("description");
                String hotkey = click.getString("hotkey");

                JSONArray behaviorsArray = click.getJSONArray("behaviors");
                List<Behavior> behaviors = new ArrayList<>();
                for (int j = 0; j < behaviorsArray.length(); j++) {
                    JSONObject behaviorObj = behaviorsArray.getJSONObject(j);
                    String typeStr = behaviorObj.getString("type");
                    int x = behaviorObj.getInt("x");
                    int y = behaviorObj.getInt("y");
                    int button = behaviorObj.getInt("button");
                    int key = behaviorObj.getInt("key");
                    int delay = behaviorObj.getInt("delay");
                    boolean isAbsolute = behaviorObj.getBoolean("isAbsolute");

                    Behavior.Type type = Behavior.Type.valueOf(typeStr);
                    behaviors.add(new Behavior(type, x, y, button, key, delay, isAbsolute));
                }

                Action action = new Action(name, description, hotkey, behaviors);

                actionsList.add(action);
                // 添加热键绑定
                addHotkeyForAction(action);
            }
        }
    }



    /**
     * 添加热键绑定来执行一个行为
     *
     * @param action 行为对象
     */
    private static void addHotkeyForAction(Action action) {
        int keyCode = getNativeKeyCode(action.getHotkey());
        if (keyCode >= NativeKeyEvent.VC_F1 && keyCode <= NativeKeyEvent.VC_F12) {
            // 仅使用功能键
            HotkeyMenuSystem.addHotkey(keyCode, () -> executeAction(action));
        } else {
            // TODO:使用修饰键 暂不考虑
            HotkeyMenuSystem.addHotkey(
                    NativeKeyEvent.CTRL_L_MASK,
                    NativeKeyEvent.CTRL_R_MASK,
                    keyCode,
                    () -> executeAction(action)
            );
        }
    }

    /**
     * 获取 NativeKeyEvent 的键码
     *
     * @param hotkey 热键标识
     * @return NativeKeyEvent 的键码
     */
    static int getNativeKeyCode(String hotkey) {

        try {
            return switch (hotkey.toUpperCase()) {
                case "F1" -> NativeKeyEvent.VC_F1; //为显示菜单功能
                case "F2" -> NativeKeyEvent.VC_F2; //为绑定窗口功能
                case "F3" -> NativeKeyEvent.VC_F3;
                case "F4" -> NativeKeyEvent.VC_F4;
                case "F5" -> NativeKeyEvent.VC_F5;
                case "F6" -> NativeKeyEvent.VC_F6;
                case "F7" -> NativeKeyEvent.VC_F7;
                case "F8" -> NativeKeyEvent.VC_F8;
                case "F9" -> NativeKeyEvent.VC_F9;
                case "F10" -> NativeKeyEvent.VC_F10;
                case "F11" -> NativeKeyEvent.VC_F11;
                case "F12" -> NativeKeyEvent.VC_F12;
                default -> throw new IllegalArgumentException("不支持的热键: " + hotkey);
            };
        } catch (IllegalArgumentException e) {
            System.out.println("不支持的热键: " + hotkey);
        }
        return 0;
    }

    /**
     * 执行一个行为
     *
     * @param action 行为对象
     */
    static void executeAction(Action action) {
        // 获取当前活动窗口PID
        long activeProcessId = getActiveWindowProcessId();
        System.out.println("当前窗口PID：" + activeProcessId);
        System.out.println("绑定窗口PID：" + boundProcessId);
        int keyCode = getNativeKeyCode(action.getHotkey());
        // 如果有绑定窗口且当前活动窗口不是绑定窗口，则不执行任何操作
        if (isCurrentWindowBound(boundProcessId, activeProcessId, keyCode)) {
            return;
        }
        for (Behavior behavior : action.getBehaviors()) {
            if (!isRunning) {
                System.out.println("停止行为操作");
                return; // 停止行为
            }

            switch (behavior.getType()) {
                case MOUSE_CLICK:
                    performMouseClick(behavior.getX(), behavior.getY(), behavior.getButton());
                    break;
                case MOUSE_PRESS:
                    performMousePress(behavior.getX(), behavior.getY(), behavior.getButton());
                    break;
                case MOUSE_RELEASE:
                    performMouseRelease(behavior.getX(), behavior.getY(), behavior.getButton());
                    break;
                case MOUSE_MOVE:
                    if (behavior.isAbsolute()) {
                        performMouseMoveAbsolute(behavior.getX(), behavior.getY());
                    } else {
                        performMouseMoveRelative(behavior.getX(), behavior.getY());
                    }
                    break;
                case KEY_PRESS:
                    performKeyPress(behavior.getKey());
                    break;
                case KEY_RELEASE:
                    performKeyRelease(behavior.getKey());
                    break;
                case KEY_CLICK:
                    performKeyPress(behavior.getKey());
                    performKeyRelease(behavior.getKey());
                    break;
                default:
                    System.err.println("不支持的行为类型: " + behavior.getType());
                    break;
            }

            // 延迟
            robot.delay(behavior.getDelay());
        }
    }

    // 判断当前窗口是否与绑定窗口相同
    private static boolean isCurrentWindowBound(long boundWindowHandle, long activeWindowHandle, int keyCode) {
        // 系统功能不受绑定窗口限制
        if (keyCode == NativeKeyEvent.VC_F1
                || keyCode == NativeKeyEvent.VC_F2
                || keyCode == NativeKeyEvent.VC_F3
                || keyCode == NativeKeyEvent.VC_F4
        ) {
            return false;
        }
        // 如果没有绑定窗口，则不执行任何操作
        if (boundWindowHandle == 0) {
            System.out.println("未绑定窗口，不执行操作");
            return true;
        }

        // 如果有绑定窗口且当前活动窗口不是绑定窗口，则不执行任何操作
        if (activeWindowHandle != boundWindowHandle) {
            System.out.println("请使用F2绑定窗口");
            System.out.println("当前活动窗口与绑定窗口不同，不执行操作");
            return true;
        }
        return false;
    }

    /**
     * 执行鼠标点击
     *
     * @param x      x坐标
     * @param y      y坐标
     * @param button 鼠标按钮
     */
    private static void performMouseClick(int x, int y, int button) {
        robot.mouseMove(x, y);
//        robot.delay(50);
        robot.mousePress(getButtonMask(button));
        robot.mouseRelease(getButtonMask(button));
        System.out.println("✅ 已在位置 (" + x + ", " + y + ") 执行点击");
    }

    /**
     * 执行鼠标按下
     *
     * @param x      x坐标
     * @param y      y坐标
     * @param button 鼠标按钮
     */
    private static void performMousePress(int x, int y, int button) {
        robot.mouseMove(x, y);
//        robot.delay(50);
        robot.mousePress(getButtonMask(button));
        System.out.println("✅ 已在位置 (" + x + ", " + y + ") 执行按下");
    }

    /**
     * 执行鼠标释放
     *
     * @param x      x坐标
     * @param y      y坐标
     * @param button 鼠标按钮
     */
    private static void performMouseRelease(int x, int y, int button) {
        robot.mouseMove(x, y);
//        robot.delay(50);
        robot.mouseRelease(getButtonMask(button));
        System.out.println("✅ 已在位置 (" + x + ", " + y + ") 执行释放");
    }

    /**
     * 将按钮编号转换为对应的 InputEvent 按钮掩码
     *
     * @param button 按钮编号
     * @return 对应的按钮掩码
     */
    private static int getButtonMask(int button) {
        return switch (button) {
            case 1 -> InputEvent.BUTTON1_DOWN_MASK;
            case 2 -> InputEvent.BUTTON2_DOWN_MASK;
            case 3 -> InputEvent.BUTTON3_DOWN_MASK;
            default -> throw new IllegalArgumentException("无效的鼠标按钮编号: " + button);
        };
    }

    /**
     * 执行鼠标绝对位置移动
     *
     * @param x x坐标
     * @param y y坐标
     */
    private static void performMouseMoveAbsolute(int x, int y) {
        robot.mouseMove(x, y);
        System.out.println("✅ 已移动到位置 (" + x + ", " + y + ")");
    }

    /**
     * 执行鼠标相对位置移动
     *
     * @param x x坐标的变化量
     * @param y y坐标的变化量
     */
    private static void performMouseMoveRelative(int x, int y) {
        Point currentMousePos = MouseInfo.getPointerInfo().getLocation();
        robot.mouseMove(currentMousePos.x + x, currentMousePos.y + y);
        System.out.println("✅ 已相对移动到位置 (" + (currentMousePos.x + x) + ", " + (currentMousePos.y + y) + ")");
    }

    /**
     * 执行键盘按下
     *
     * @param key 键值
     */
    private static void performKeyPress(int key) {
//        ensureFocus();
        robot.keyPress(key);
        System.out.println("✅ 已按下键 " + KeyEvent.getKeyText(key) + ":" + key);
    }

    /**
     * 执行键盘释放
     *
     * @param key 键值
     */
    private static void performKeyRelease(int key) {
//        ensureFocus();
        robot.keyRelease(key);
        System.out.println("✅ 已释放键 " + KeyEvent.getKeyText(key) + ":" + key);
    }

    /**
     * 停止所有行为操作
     */
    private static void stopAllClicks() {
        isRunning = false;
        System.out.println("停止所有行为操作");
    }

    // 获取当前活动窗口的进程ID
    public static int getActiveWindowProcessId() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
        return processId.getValue();
    }
}
