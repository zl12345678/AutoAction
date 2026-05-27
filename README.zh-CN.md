# AutoAction Studio 中文说明

AutoAction Studio 是一个偏向 Windows 的桌面自动化工作台，主要面向游戏和其他富客户端场景。它把基于 OpenCV 的小地图导航、OCR、可选的 YOLO 检测、UI Automation 辅助规则，以及鼠标键盘执行能力收在同一个 Compose Desktop 应用里。

当前仓库以 Gradle 作为主构建链，默认桌面入口是 `com.auto.AutoActionStudio`。

## 项目里有什么

- Compose Desktop 工作台，用来做截图、分析、预览和执行
- 从资源目录加载的 OpenCV 4.12 本地依赖
- 自带英文 `tessdata` 的 Tesseract OCR
- 基于 ONNX Runtime 的可选 YOLO 检测
- 通过 PowerShell 接入的 Windows UI Automation 辅助能力

## 目录结构

- `src/main/kotlin/com/auto/ui`
  - Compose Desktop 界面和状态管理
- `src/main/java/com/auto/vision`
  - OpenCV 加载、导航流程、路径规划、点击预览
- `src/main/java/com/auto/ocr`
  - OCR 服务和区域预处理
- `src/main/java/com/auto/detection`
  - YOLO 配置和 ONNX 目标检测
- `src/main/java/com/auto/uiautomation`
  - Windows UI Automation 命令桥
- `src/main/java/com/auto/input`
  - 基于 `Robot` 的输入控制
- `src/main/java/com/auto/window`
  - 窗口查找和屏幕截图
- `src/main/resources`
  - 默认配置、示例图片、OpenCV 本地文件、`tessdata`

## 构建和运行

在仓库根目录执行：

```bash
./gradlew test
./gradlew run
```

Windows 下：

```powershell
.\gradlew.bat test
.\gradlew.bat run
```

常用打包任务：

```bash
./gradlew packageDistributionForCurrentOS
./gradlew createDistributable
```

## 主要入口

- `com.auto.AutoActionStudio`
  - 当前默认的 Compose Desktop 图形界面

## 默认配置

程序会优先读取工作目录下的 `autoActionConfig.json`。如果没有，再回退到 `src/main/resources/autoActionConfig.json`。

配置目前主要分成三块：

- `system`
  - 运行时全局开关，例如 `dryRun`
- `vision`
  - 窗口标题、小地图 ROI、目标点、地图模板路径，以及 OCR / YOLO 相关配置
- `uiAutomation`
  - 辅助命令、超时和规则列表

## 寻路地图制作

工作台左侧新增 **寻路地图制作** 面板，用于从游戏窗口截图生成可供 `PathPlanner` 使用的二值地图：

1. 在游戏中打开大地图界面（未开游戏可跳过，改用本地上传）
2. 调整 **地图 ROI**，框住地图区域（配置项 `vision.mapPreprocess.mapRegion`）
3. 点击 **捕获并提取**；或 **上传窗口截图**（整窗截图 + ROI 裁剪）；若文件本身已是地图，用 **上传地图原图**
4. 也可先 **捕获窗口** / **加载本地图像**，再点 **从当前截图提取**
5. 在 **地图原图** / **寻路地图** 预览页检查效果，微调阈值、Otsu、去除橙色标记等参数
6. 边界附近若有 **人物箭头**，可勾选 **去除橙色标记**
7. 点击 **生成寻路地图**（此步骤不做边界密封）
8. 在右侧 **闭合检测** 面板点 **检测闭合** 或 **预览密封效果**；未闭合时 **导出供编辑** → 补墙 → **导入已编辑**
9. 满意后 **应用为导航地图**

手动编辑约定：**白色 = 障碍/墙，黑色 = 可走**。导入时会自动二值化。

闭合检测参数在 `vision.mapClosure`（与制作分离）；密封预览仅用于测试，不会自动写回制作中的寻路图。

生成的地图会写入 `vision.mapImage` 路径，供后续小地图定位与 A* 寻路使用。可走阈值应与导航里的 **障碍阈值** 一致（默认 200：像素值大于该值为障碍）。

## 自动导航寻路（端到端）

游戏在前台、配置正确时，**开始连续导航** 会按固定周期自动执行：

```text
捕获游戏窗口 → 小地图箭头 → 大地图匹配定位 → mapClosure 密封地图上 A* → 屏幕点击 → 下一航点
```

### 前置条件

1. `autoActionConfig.json`：`system.dryRun: false`，`vision.windowTitle` 与游戏窗口标题一致
2. 已制作并 **应用为导航地图** 的 `vision.mapImage`（可走/障碍与 `obstacleThreshold` 一致）
3. 小地图 ROI（`miniMapRegion`）框住角色箭头区域
4. 目标点 `vision.target` 为地图坐标（可在地图路径预览上确认）
5. 点击后端：`input.clickBackend` 为 `interception`（需装好驱动）或 `win32`（部分游戏无效）
6. 建议完成 **屏幕标定**（导航配置里 ≥3 组 map/screen 点对并启用 `screenCalibration`），否则落点可能偏

### 操作步骤

1. `./gradlew run` 打开 Compose 工作台 → **重新加载配置**
2. **捕获窗口**，确认源图与小地图 ROI
3. 调试面板 **步骤 1–8** 全部通过（尤其 4 定位、6 路径、8 点击）
4. 取消 **Dry Run**
5. 点击 **一键预检**（左侧「项目」或右侧「分析与执行」），全部通过后再点 **开始连续导航**
6. 到达目标或失败后自动停止，状态栏显示航点与置信度

### 配置示例

```json
{
  "input": { "clickBackend": "interception", "interceptionHome": "D:\\Desktop\\Interception" },
  "system": { "dryRun": false },
  "vision": {
    "windowTitle": "Torchlight: Infinite  ",
    "mapImage": "img/sggd/largeMap_2.bmp",
    "target": { "x": 779, "y": 285 },
    "miniMapRegion": { "x": 64, "y": 86, "width": 84, "height": 80 }
  }
}
```

## 工作台使用流程

Compose 工作台现在主要围绕这一条调试路径：

1. 点击 `重新加载配置`
2. 点击 `运行示例`，先验证仓库自带图片
3. 点击 `捕获窗口` 或 `加载本地图像`
4. 点击 `处理当前图像`，看灰度或阈值结果
5. 点击 `分析` 或 `点击预览`
6. 在叠加图和日志都看顺眼之前，先保持 `Dry Run`
7. 调试步骤 8 通过后，取消 Dry Run，点击 **开始连续导航**

当前预览标签主要保留：

- 源图
- 处理结果
- 小地图分析
- 地图路径
- 点击预览

为了先把主流程收紧，Compose 界面里的 OCR / YOLO 配置面板、结果区和对应预览标签目前是隐藏的。底层识别链路和配置结构还保留在代码里，后面可以再放出来。

## OCR、YOLO 和 UI Automation

- OCR 基于 Tess4J，仓库自带 `eng.traineddata`
- YOLO 是可选能力，只有在 `vision.yolo.enabled` 为 `true` 时才会运行
- 示例配置里 OCR 和 YOLO 默认都是关闭的
- 仓库不自带 YOLO 的 ONNX 模型，因此 `vision.yolo.modelPath` 和 `labelsPath` 需要指向真实存在的本地文件
- 当前 Compose 界面先隐藏了 OCR / YOLO 控件；如果要提前使用，需要直接改配置或走代码接入
- UI Automation 规则通过 `tools/windows-ui-automation-helper.ps1` 调度
- 这部分更适合处理标准 Windows 对话框和控件；游戏世界里的交互仍然主要依赖截图分析和输入模拟

## 本地依赖

OpenCV 相关资源位于 `src/main/resources/lib/opencv`：

- `opencv_java4120.dll`
- `opencv_world4120.dll`
- `opencv-4120.jar`
- `opencv.properties`

加载器会先读取 `opencv.properties`，按需要预加载依赖库，再加载 Java 绑定 DLL，这样本地运行和打包运行的行为会更一致。

## 测试

当前测试覆盖了：

- 配置解析和校验
- 路径规划和导航分析
- YOLO 后处理
- OCR 区域执行
- UI Automation helper 的请求 / 响应处理

运行方式：

```bash
./gradlew test
```

## 备注

- 这个项目整体偏向 Windows，因为窗口捕获、`Robot` 和 UI Automation 都是主要工作流的一部分
- `pom.xml` 目前还在仓库里，但已经属于遗留元数据，实际维护以 Gradle 方案为主
