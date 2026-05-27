package com.auto.vision;

public enum NavigationDebugStep {
    CAPTURE_WINDOW(
            1,
            "捕获窗口",
            "查找游戏窗口并截图，作为后续步骤输入。"
    ),
    CROP_MINIMAP(
            2,
            "裁剪小地图",
            "按小地图 ROI 从源图裁剪，检查框选范围是否正确。"
    ),
    MATCH_ARROW(
            3,
            "识别箭头",
            "在小地图中识别角色橙色箭头及匹配置信度。"
    ),
    LOCATE_ON_MAP(
            4,
            "大地图定位",
            "将小地图特征匹配到寻路大地图，得到当前地图坐标。"
    ),
    PLAN_PATH(
            5,
            "路径规划",
            "A* 计算从当前位置到目标点的可走路径。"
    ),
    MAP_TO_SCREEN(
            6,
            "屏幕映射",
            "将下一路点转换为屏幕点击坐标（标定或 fallback）。"
    ),
    NAV_DECISION(
            7,
            "导航决策",
            "模拟 NavigationController：判断跳过点击、发点击或卡住。"
    ),
    EXECUTE_CLICK(
            8,
            "执行点击",
            "真实移动鼠标并左键点击。Dry Run 开启时本步会失败且不执行任何操作。"
    );

    private final int order;
    private final String label;
    private final String hint;

    NavigationDebugStep(int order, String label, String hint) {
        this.order = order;
        this.label = label;
        this.hint = hint;
    }

    public int order() {
        return order;
    }

    public String label() {
        return label;
    }

    public String hint() {
        return hint;
    }
}
