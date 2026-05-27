# AutoAction Studio

AutoAction Studio is a Windows desktop automation workbench for games and other rich clients. It combines OpenCV-based minimap navigation, OCR, optional YOLO detection, UI Automation helper rules, and direct mouse/keyboard execution in one Compose Desktop app.

The repo now builds with Gradle as the primary toolchain. The default desktop entry point is `com.auto.AutoActionStudio`.

For a Chinese guide, see [README.zh-CN.md](README.zh-CN.md).

## What is in here

- Compose Desktop workbench for capture, analysis, preview, and execution
- OpenCV 4.12 native loading from packaged resources
- Tesseract OCR with bundled English `tessdata`
- Optional ONNX Runtime YOLO inference
- Windows UI Automation helper integration through PowerShell

## Project layout

- `src/main/kotlin/com/auto/ui`
  - Compose Desktop UI and state management
- `src/main/java/com/auto/vision`
  - OpenCV loading, navigation pipeline, pathing, click preview
- `src/main/java/com/auto/ocr`
  - OCR service and region-based preprocessing
- `src/main/java/com/auto/detection`
  - YOLO config and ONNX-based object detection
- `src/main/java/com/auto/uiautomation`
  - Windows UI Automation command bridge
- `src/main/java/com/auto/input`
  - `Robot`-based input control
- `src/main/java/com/auto/window`
  - Window lookup and screen capture
- `src/main/resources`
  - Default config, sample images, OpenCV natives, tessdata

## Build and run

Use the Gradle wrapper from the repo root:

```bash
./gradlew test
./gradlew run
```

On Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat run
```

Useful packaging tasks:

```bash
./gradlew packageDistributionForCurrentOS
./gradlew createDistributable
```

## Main entry points

- `com.auto.AutoActionStudio`
  - Compose Desktop GUI, now the default app entry

## Default config

The app loads `autoActionConfig.json` from the working directory first. If it is not present, it falls back to `src/main/resources/autoActionConfig.json`.

The config currently contains three main blocks:

- `system`
  - global runtime flags such as `dryRun`
- `vision`
  - window title, minimap ROI, target point, map/template paths, OCR, YOLO
- `uiAutomation`
  - optional helper command, timeout, and rule list

## Workbench flow

The Compose workbench is organized around a practical debug loop:

1. `重新加载配置` to pick up config changes
2. `运行示例` to validate the pipeline with bundled images
3. `捕获窗口` or `加载本地图像`
4. `处理当前图像` to inspect grayscale / threshold output
5. `分析` or `点击预览`
6. keep `Dry Run` on until the overlays and logs look right
7. run `执行单步` or `开始连续导航`

Preview tabs currently focus on source, processed image, minimap analysis, map path, and click preview.

The OCR and YOLO pipelines are still available in the codebase and config, but their Compose UI panels and preview tabs are temporarily hidden while the main workbench flow is being simplified.

## OCR, YOLO, and UI Automation

- OCR uses Tess4J with bundled `eng.traineddata`
- YOLO is optional and only runs when `vision.yolo.enabled` is true
- The sample config ships with both OCR and YOLO disabled by default; enable them in the Studio panel or `src/main/resources/autoActionConfig.json`
- The repository does not bundle a YOLO ONNX model, so `vision.yolo.modelPath` and `labelsPath` must point to real local files before detections can appear
- The current Compose UI keeps OCR and YOLO controls hidden for now; use config changes or code-level integration if you need to exercise them before the panels return
- UI Automation rules are optional and dispatch through `tools/windows-ui-automation-helper.ps1`
- The helper is useful for standard Windows dialogs and controls; game-world interaction still relies on capture + input simulation

## Native dependencies

OpenCV resources live under `src/main/resources/lib/opencv`:

- `opencv_java4120.dll`
- `opencv_world4120.dll`
- `opencv-4120.jar`
- `opencv.properties`

The loader reads `opencv.properties`, preloads any dependent native libraries, and then loads the configured Java binding DLL. This keeps local runs and packaged runs aligned.

## Tests

Current tests cover:

- config parsing and validation
- path planning and navigation analysis
- YOLO post-processing
- OCR region execution
- UI Automation helper request/response handling

Run them with:

```bash
./gradlew test
```

## Notes

- This project is Windows-oriented because capture, `Robot`, and UI Automation are all part of the intended workflow.
- `pom.xml` is still present as legacy metadata, but Gradle is the maintained build path.
