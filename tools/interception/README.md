# Interception 驱动级鼠标（罗技 / 任意 USB 鼠标）

火炬之光等 **Unreal** 游戏会忽略 `SendInput` / `PostMessage`。  
**Interception** 在 Windows 输入驱动层注入，你的 **罗技鼠标无需单独 SDK**，与普通 USB 鼠标一样走系统鼠标通道。

## 1. 安装驱动（一次性）

**你当前的报错**（`DLL 已加载，但 interception_create_context 返回 null`）= **内核驱动未加载**，与 DLL 无关。

### 推荐安装方式（避免 “Could not write to System32\drivers”）

在 **普通** PowerShell 里执行（不要右键“以管理员”），让安装程序自己弹 UAC：

```powershell
cd "D:\Desktop\Interception\command line installer"
.\install-interception.exe /install
```

- 成功时控制台会有明确成功提示（不是 `Could not write`）。
- 然后 **必须重启电脑**（仅注销不够）。

### 安装后自检

```powershell
cd E:\work\AutoAction
powershell -ExecutionPolicy Bypass -File tools\interception\install-check.ps1
```

应看到 `[OK] 可打开 \\.\interception00`。

### 仍失败时

```powershell
.\install-interception.exe /uninstall   # 普通 PS + UAC
# 重启
.\install-interception.exe /install
# 再重启
```

- 设置 → Windows 安全中心 → 设备安全性 → **内核隔离 → 内存完整性：关**（部分 Win11 会拦旧驱动）
- 确认 `C:\Windows\System32\drivers\keyboard.sys` 与 `mouse.sys` 存在（Interception 安装后会有）

## 2. 放置 DLL

**必须用** 安装包里的 **x64** 版本（约 11264 字节），不要用 `.lib` 或其它目录里的旧 DLL：

```
D:\Desktop\Interception\library\x64\interception.dll
  → 复制到 →
E:\work\AutoAction\tools\interception\interception.dll
```

或在 `autoActionConfig.json` 指定安装目录（无需复制）：

```json
"input": {
  "clickBackend": "interception",
  "interceptionHome": "D:\\Desktop\\Interception"
}
```

## 3. 修改配置

在 `autoActionConfig.json` 增加：

```json
{
  "input": {
    "clickBackend": "interception"
  },
  "system": {
    "dryRun": false
  }
}
```

## 4. 运行

```powershell
./gradlew run
```

成功时日志类似：

```
GameWindowClick: Interception 驱动点击 屏幕(849,639) ×1
```

若 DLL/驱动未就绪，会回退到软件注入并提示本说明路径。

## 罗技 G HUB 说明

- G HUB **Lua 脚本不能**从 AutoAction 读取坐标（沙箱无 `io`）。
- G HUB 的 `MoveMouseTo` / `PressMouseButton` 仍是罗技软件层，对 UE Raw Input 游戏**不一定**比 SendInput 更好。
- **推荐**：Interception + 罗技鼠标；G HUB 可保持开启，但不要占用同一侧键做冲突宏。

## 风险

- 内核驱动需信任来源；仅建议本机自动化使用。
- 部分网游反作弊可能仍检测异常输入；请自行承担账号风险。
