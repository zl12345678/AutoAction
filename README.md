### AutoAction 项目 README 文档

#### 项目简介
为学习Java自动化与OpenCV诞生的一个项目。
`AutoAction` 是一个自动点击器工具，支持通过热键触发预定义的行为（如鼠标点击、键盘按键等）。用户可以通过配置文件 (`clickConfig.json`) 来定义不同的行为，并且可以绑定特定的窗口以确保行为只在该窗口中执行。


#### 功能特性
- **热键触发**：支持使用功能键（F1-F12）或组合键（Ctrl + 键）来触发行为。
- **行为定义**：通过 JSON 文件配置各种行为，包括鼠标点击、按下、释放、移动和键盘按键等。
- **窗口绑定**：可以绑定当前活动窗口的进程 ID，确保行为只在指定窗口中执行。
- **停止所有行为**：通过 Ctrl + Esc 组合键停止所有正在执行的行为。
- **初始菜单显示**：启动时显示行为列表菜单，方便用户选择和管理。
- **接入OpenCV**：通过 OpenCV 进行图像识别和操作，可以自动识别和操作窗口中的元素，并进行自动化操作（暂未实现）。

#### 环境依赖
- **Java**：Java 17
- **JNA (Java Native Access)**：用于与 Windows API 进行交互
- **JNativeHook**：用于全局热键监听

#### 使用说明

##### 1. 配置文件
配置文件路径：`src/main/resources/clickConfig.json`

示例配置：
```json
{
  "clicks": [
    {
      "name": "POS2",
      "description": "画三角形",
      "hotkey": "F6",
      "behaviors": [
        {
          "type": "MOUSE_MOVE",
          "x": 910,
          "y": 490,
          "button": 0,
          "key": 0,
          "delay": 100,
          "isAbsolute": true
        },
        {
          "type": "MOUSE_PRESS",
          "x": 910,
          "y": 490,
          "button": 1,
          "key": 0,
          "delay": 100,
          "isAbsolute": true
        },
        {
          "type": "MOUSE_MOVE",
          "x": 1010,
          "y": 690,
          "button": 0,
          "key": 0,
          "delay": 100,
          "isAbsolute": true
        },
        {
          "type": "MOUSE_MOVE",
          "x": 810,
          "y": 690,
          "button": 0,
          "key": 0,
          "delay": 100,
          "isAbsolute": true
        },
        {
          "type": "MOUSE_MOVE",
          "x": 910,
          "y": 490,
          "button": 0,
          "key": 0,
          "delay": 100,
          "isAbsolute": true
        },
        {
          "type": "MOUSE_RELEASE",
          "x": 910,
          "y": 490,
          "button": 1,
          "key": 0,
          "delay": 100,
          "isAbsolute": true
        }
      ]
    }
  ]
}
```

- `name`：行为名称
- `description`：行为描述
- `hotkey`：触发行为的热键
- `behaviors`：行为列表，每个行为包含类型（如 `MOUSE_CLICK`）、坐标、按钮、键值、延迟时间和是否为绝对坐标等属性
   - `type`：行为类型，如 `MOUSE_CLICK`、`MOUSE_PRESS`、`MOUSE_RELEASE`、`MOUSE_MOVE`、`KEY_PRESS`、`KEY_RELEASE` 等
   - `x`、`y`：坐标，仅在 `isAbsolute` 为 `true` 时有效，表示绝对坐标
   - `button`：鼠标按钮，如 `1`（左键）、`2`（中键）、`3`（右键）等
   - `key`：键值，仅在 `type` 为 `KEY_PRESS` 或 `KEY_RELEASE` 时有效，表示按键的键值
   - `delay`：延迟时间，单位为毫秒，表示执行该行为的延迟时间
   - `isAbsolute`：是否为绝对坐标，默认为 `false`，表示相对坐标（当前鼠标位置的相对坐标）
##### 2. 编译与运行
1. **编译项目**：
   ```bash
   mvn clean install
   ```


2. **运行项目**：
   ```bash
   java -jar target/AutoAction-1.0-SNAPSHOT.jar
   ```


##### 3. 热键操作
- **F1**：显示行为列表菜单
- **F2**：绑定当前活动窗口
- **F3**: 接入OpenCV实现识图画图（暂未实现）
- **F4**: 重新加载自定义配置（暂未实现）
- **Ctrl + Esc**：停止所有行为操作
- **其他功能键（F5-F12）**：根据配置文件中的定义触发相应行为

##### 4. 窗口绑定
通过 F2 热键绑定当前活动窗口的进程 ID。绑定后，只有当该窗口处于活动状态时，才会执行相应的行为。

##### 5. 测试
在测试过程中，请确保配置文件中的行为定义正确，并且窗口绑定正确。可在画图软件中，通过F2绑定窗口后，然后按对应热键，可以触发对应的行为。

#### 代码结构
- **`org.example.AutoClicker`**：主类，负责初始化和管理行为列表、热键绑定及行为执行。
- **`org.example.entity.Action`**：行为对象，包含行为名称、描述、热键和具体行为列表。
- **`org.example.entity.Behavior`**：具体行为对象，定义了行为类型（如鼠标点击、键盘按键等）及其参数。
- **`org.example.HotkeyMenuSystem`**：热键管理类，负责注册和处理热键事件。

#### 注意事项
- **权限问题**：某些操作系统可能需要管理员权限才能监听全局热键，请确保以管理员身份运行程序。
- **安全性**：自动点击器可能会被一些应用程序视为自动化脚本，使用时请注意遵守相关软件的使用条款。

#### 贡献指南
欢迎贡献代码或提出改进建议！请遵循以下步骤：
1. Fork 项目仓库。
2. 创建新分支 (`git checkout -b feature/YourFeature`)。
3. 提交更改 (`git commit -am 'Add some feature'`)。
4. 推送到远程分支 (`git push origin feature/YourFeature`)。
5. 提交 Pull Request。

#### 联系方式
如有任何问题或建议，请联系 [770736945@qq.com] 或提交 Issue。

---

希望这份 README 文档能帮助你更好地理解和使用 `AutoAction` 项目。如果你有任何疑问或需要进一步的帮助，请随时联系项目维护者。