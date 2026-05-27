package com.auto.ui

import com.auto.capture.CaptureAnalysis
import com.auto.capture.CaptureAnalysisService
import com.auto.config.AppConfig
import com.auto.config.AppConfigLoader
import com.auto.config.MapClosureConfig
import com.auto.config.MapPreprocessConfig
import com.auto.config.NavigationConfig
import com.auto.config.RegionConfig
import com.auto.config.OcrConfig
import com.auto.config.OcrRegionConfig
import com.auto.config.OcrRegionSource
import com.auto.config.PointConfig
import com.auto.config.ScreenCalibrationConfig
import com.auto.config.ScreenCalibrationPointConfig
import com.auto.config.UiAutomationActionConfig
import com.auto.config.UiAutomationConfig
import com.auto.config.UiAutomationPattern
import com.auto.config.UiAutomationRuleConfig
import com.auto.config.VisionConfig
import com.auto.config.YoloConfig
import com.auto.detection.DetectedObject
import com.auto.input.InputController
import com.auto.input.RobotInputController
import com.auto.opencv.utils.ImageProcessor
import com.auto.uiautomation.UiAutomationElement
import com.auto.uiautomation.UiAutomationRuleExecution
import com.auto.uiautomation.UiAutomationService
import com.auto.uiautomation.WindowsUiAutomationHelperService
import com.auto.vision.NavigationAnalysis
import com.auto.vision.OpenCvLoader
import com.auto.vision.NavigationDebugStep
import com.auto.vision.NavigationDebugStepResult
import com.auto.input.interception.InterceptionBootstrap
import com.auto.vision.NavigationPipelineDebugger
import com.auto.vision.NavigationPreflightService
import com.auto.vision.OpenCvNavigationAnalyzer
import com.auto.vision.NavigationRuntimeStatus
import com.auto.vision.OpenCvNavigationPipeline
import com.auto.vision.VisionNavigationService
import com.auto.vision.PathfindingMapClosureAnalyzer
import com.auto.vision.PathfindingMapImporter
import com.auto.vision.PathfindingMapPreprocessor
import com.auto.window.AwtJnaWindowService
import com.auto.window.WindowRef
import com.auto.window.WindowService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Optional

class StudioViewModel(
    private val configLoader: AppConfigLoader = AppConfigLoader(),
    private val configWriter: com.auto.config.AppConfigWriter = com.auto.config.AppConfigWriter(),
    private val windowService: WindowService = AwtJnaWindowService(),
    private val inputController: InputController = RobotInputController(),
    private val captureAnalysisService: CaptureAnalysisService = CaptureAnalysisService(),
    private val visionNavigationService: VisionNavigationService = VisionNavigationService(
        windowService,
        inputController,
        OpenCvNavigationPipeline(windowService)
    ),
    private val uiAutomationService: UiAutomationService = WindowsUiAutomationHelperService(),
    private val navigationAnalyzer: OpenCvNavigationAnalyzer = OpenCvNavigationAnalyzer(),
    private val navigationPipelineDebugger: NavigationPipelineDebugger = NavigationPipelineDebugger(
        windowService,
        navigationAnalyzer,
        inputController
    ),
    private val pathfindingMapPreprocessor: PathfindingMapPreprocessor = PathfindingMapPreprocessor(),
    private val pathfindingMapClosureAnalyzer: PathfindingMapClosureAnalyzer = PathfindingMapClosureAnalyzer(),
    private val navigationPreflightService: NavigationPreflightService = NavigationPreflightService(windowService),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    private var loadedConfig: AppConfig? = null
    private var currentSourceImage: BufferedImage? = null
    private var currentSourceBounds: Rectangle? = null
    private var currentSourceName: String = "未加载图像"
    private var lastCaptureAnalysis: CaptureAnalysis? = null

    init {
        OpenCvLoader.load()
        visionNavigationService.setStatusListener { status ->
            updateState {
                val liveMap = status.liveMapPreviewImage()
                copy(
                    navigationRuntimeText = formatNavigationRuntime(status),
                    navigationWaypointIndex = status.waypointIndex(),
                    navigationConfidenceText = "%.2f".format(status.localizationConfidence()),
                    navigationRunning = visionNavigationService.isRunning,
                    mapPreviewImage = liveMap ?: mapPreviewImage,
                    currentPointText = formatLocalizationPoint(status),
                    localizationDetailText = status.localizationDetail(),
                    selectedPreviewTab = if (visionNavigationService.isRunning && liveMap != null) {
                        PreviewTab.MAP
                    } else {
                        selectedPreviewTab
                    }
                )
            }
        }
        loadConfig()
    }

    fun loadConfig() {
        scope.launch {
            runAction("配置加载") {
                val config = configLoader.loadDefault()
                loadedConfig = config
                applyConfig(config)
                appendLog("配置加载成功: ${AppConfigLoader.DEFAULT_RESOURCE}")
                setStatus("配置加载完成")
            }
        }
    }

    fun loadLocalImage(file: File) {
        scope.launch {
            runAction("本地图像") {
                val image = readLocalImage(file)
                setSourceImage(image, Rectangle(0, 0, image.width, image.height), file.absolutePath)
                appendLog("已加载本地图像: ${file.absolutePath}")
                setStatus("本地图像已加载")
            }
        }
    }

    /**
     * Loads a full game window screenshot from disk and extracts the map crop using [mapRegion].
     * Use this to test the map pipeline without running the game.
     */
    fun loadLocalScreenshotForMap(file: File) {
        scope.launch {
            runAction("本地窗口截图") {
                val image = readLocalImage(file)
                setSourceImage(image, Rectangle(0, 0, image.width, image.height), "local:${file.absolutePath}")
                updateState { copy(sourceMode = StudioSourceMode.WINDOW) }
                applyMapPreprocess(image)
                appendLog(
                    "已加载本地窗口截图并提取地图: ${file.name} (${image.width}x${image.height}), " +
                        "ROI=${regionText(buildMapPreprocessConfig().mapRegion())}"
                )
                setStatus("本地截图已提取地图，可在「地图原图」「寻路地图」预览")
            }
        }
    }

    /**
     * Loads an already-cropped map image (the whole file is the map). Skips ROI extraction.
     */
    fun loadLocalMapImage(file: File) {
        scope.launch {
            runAction("本地地图原图") {
                val image = readLocalImage(file)
                setSourceImage(image, Rectangle(0, 0, image.width, image.height), "local-map:${file.absolutePath}")
                applyMapPreprocess(image, fromOriginalOnly = true)
                appendLog("已加载本地地图原图: ${file.name} (${image.width}x${image.height})")
                setStatus("本地地图原图已加载，可微调参数后生成寻路地图")
            }
        }
    }

    fun loadSampleImageOnly() {
        scope.launch {
            runAction("示例截图") {
                val sample = ImageProcessor.loadResourceBufferedImage("img/gamePic.bmp")
                setSourceImage(sample, Rectangle(0, 0, sample.width, sample.height), "resource:img/gamePic.bmp")
                updateState { copy(sourceMode = StudioSourceMode.WINDOW) }
                appendLog("已加载示例截图")
                setStatus("示例截图已加载")
            }
        }
    }

    fun runSampleAnalysis() {
        scope.launch {
            runAction("示例分析") {
                val config = buildVisionConfig()
                var analysis = captureAnalysisService.analyzeSample(config)
                if (!analysis.navigationAnalysis().success()) {
                    val demoConfig = VisionConfig(
                        config.windowTitle(),
                        config.mapImage(),
                        config.arrowTemplate(),
                        config.miniMapRegion(),
                        PointConfig(500, 260),
                        config.matchAreaSize(),
                        config.obstacleThreshold(),
                        config.moveStep(),
                        config.arriveDistance(),
                        config.ocr(),
                        config.yolo(),
                        config.mapPreprocess(),
                        config.mapClosure(),
                        config.navigation()
                    )
                    analysis = captureAnalysisService.analyzeSample(demoConfig)
                    appendLog("当前目标点在示例图上不可达，示例已切换到演示目标点 (500, 260)。")
                }
                updateState { copy(sourceMode = StudioSourceMode.WINDOW) }
                currentSourceBounds = Rectangle(0, 0, analysis.sourceImage()!!.width, analysis.sourceImage()!!.height)
                applyCaptureAnalysis(analysis)
                setStatus("示例分析完成")
            }
        }
    }

    fun captureAndExtractGameMap() {
        scope.launch {
            runAction("捕获并提取地图") {
                val config = buildVisionConfig()
                val window = windowService.findWindow(config.windowTitle())
                if (window.isEmpty) {
                    val message = "未找到窗口: ${config.windowTitle()}"
                    setStatus(message)
                    appendLog(message)
                    return@runAction
                }
                val windowRef = window.get()
                val capture = windowService.capture(windowRef.bounds())
                setSourceImage(capture, windowRef.bounds(), "window:${windowRef.title()}")
                updateState { copy(sourceMode = StudioSourceMode.WINDOW) }
                applyMapPreprocess(capture)
                appendLog("已捕获窗口并提取地图 ROI: ${regionText(buildMapPreprocessConfig().mapRegion())}")
            }
        }
    }

    fun extractMapFromCurrentCapture() {
        scope.launch {
            runAction("提取地图原图") {
                val capture = currentSourceImage ?: run {
                    appendLog("请先捕获游戏窗口或加载一张完整窗口截图。")
                    setStatus("先准备窗口截图")
                    return@runAction
                }
                applyMapPreprocess(capture)
            }
        }
    }

    fun loadClosureTestImage(file: File) {
        scope.launch {
            runAction("上传闭合检测图") {
                val image = readLocalImage(file)
                updateState {
                    copy(
                        closureTestImage = image,
                        closureTestImageName = file.name,
                        mapClosureLeakPreview = null,
                        mapClosureSealPreview = null,
                        mapClosureClosed = null,
                        mapClosureSummary = "",
                        selectedPreviewTab = PreviewTab.MAP_CLOSURE_INPUT
                    )
                }
                appendLog("已加载闭合检测图: ${file.absolutePath} (${image.width}x${image.height})")
                setStatus("闭合检测图已加载")
            }
        }
    }

    fun useCurrentPathfindingMapForClosureTest() {
        scope.launch {
            runAction("载入寻路图到闭合检测") {
                val map = _state.value.mapPathfindingImage ?: run {
                    appendLog("请先生成或导入寻路地图。")
                    setStatus("缺少寻路地图")
                    return@runAction
                }
                updateState {
                    copy(
                        closureTestImage = ImageProcessor.copyBufferedImage(map),
                        closureTestImageName = "寻路地图",
                        mapClosureLeakPreview = null,
                        mapClosureSealPreview = null,
                        mapClosureClosed = null,
                        mapClosureSummary = "",
                        selectedPreviewTab = PreviewTab.MAP_CLOSURE_INPUT
                    )
                }
                appendLog("已将当前寻路地图设为闭合检测对象。")
                setStatus("闭合检测图已更新")
            }
        }
    }

    fun testMapClosure() {
        scope.launch {
            runAction("闭合检测") {
                val map = _state.value.closureTestImage ?: run {
                    appendLog("请先上传闭合检测用寻路地图。")
                    setStatus("缺少闭合检测图")
                    return@runAction
                }
                val analysis = pathfindingMapClosureAnalyzer.analyze(map, buildMapClosureConfig())
                applyClosureAnalysis(analysis, PreviewTab.MAP_CLOSURE_LEAK)
            }
        }
    }

    fun previewMapClosureSeal() {
        scope.launch {
            runAction("密封预览") {
                val map = _state.value.closureTestImage ?: run {
                    appendLog("请先上传闭合检测用寻路地图。")
                    setStatus("缺少闭合检测图")
                    return@runAction
                }
                val analysis = pathfindingMapClosureAnalyzer.previewSeal(map, buildMapClosureConfig())
                applyClosureAnalysis(analysis, PreviewTab.MAP_CLOSURE_SEAL)
            }
        }
    }

    fun refreshPathfindingMap() {
        scope.launch {
            runAction("生成寻路地图") {
                val original = _state.value.mapOriginalImage ?: run {
                    appendLog("请先捕获并提取地图原图。")
                    setStatus("缺少地图原图")
                    return@runAction
                }
                applyMapPreprocess(original, fromOriginalOnly = true)
            }
        }
    }

    fun saveMapOriginalImage(file: File) {
        scope.launch {
            runAction("保存地图原图") {
                val image = _state.value.mapOriginalImage ?: throw IllegalStateException("没有可保存的地图原图")
                javax.imageio.ImageIO.write(image, extensionOf(file), file)
                appendLog("地图原图已保存: ${file.absolutePath}")
                setStatus("地图原图已保存")
            }
        }
    }

    fun savePathfindingMapImage(file: File) {
        scope.launch {
            runAction("保存寻路地图") {
                val image = _state.value.mapPathfindingImage ?: throw IllegalStateException("没有可保存的寻路地图")
                javax.imageio.ImageIO.write(image, extensionOf(file), file)
                appendLog("寻路地图已保存: ${file.absolutePath}")
                setStatus("寻路地图已保存")
            }
        }
    }

    fun exportMapForManualEdit(pathfindingFile: File) {
        scope.launch {
            runAction("导出供手动编辑") {
                val pathfinding = _state.value.mapPathfindingImage ?: throw IllegalStateException("请先生成寻路地图")
                val parent = pathfindingFile.toPath().parent ?: Path.of(".")
                Files.createDirectories(parent)
                javax.imageio.ImageIO.write(pathfinding, extensionOf(pathfindingFile), pathfindingFile)
                _state.value.mapOriginalImage?.let { original ->
                    val sidecar = File(pathfindingFile.parentFile, "${pathfindingFile.nameWithoutExtension}_original.bmp")
                    javax.imageio.ImageIO.write(original, "bmp", sidecar)
                    appendLog("已同时导出地图原图: ${sidecar.absolutePath}")
                }
                appendLog("寻路地图已导出: ${pathfindingFile.absolutePath}")
                appendLog(manualEditHint())
                setStatus("已导出，请在外部编辑器修补后导入")
            }
        }
    }

    fun importEditedPathfindingMap(file: File) {
        scope.launch {
            runAction("导入已编辑寻路地图") {
                val normalized = PathfindingMapImporter.normalizeEditedMap(readLocalImage(file))
                updateState {
                    copy(
                        mapPathfindingImage = normalized,
                        selectedPreviewTab = PreviewTab.MAP_PATHFINDING,
                        status = "已导入手动编辑的寻路地图"
                    )
                }
                appendLog("已导入编辑后的寻路地图: ${file.absolutePath} (${normalized.width}x${normalized.height})")
                appendLog("导入结果已二值化：黑=可走，白=障碍。确认无误后可「应用为导航地图」。")
            }
        }
    }

    fun applyPathfindingMapToNavigation(file: File) {
        scope.launch {
            runAction("应用寻路地图") {
                val image = _state.value.mapPathfindingImage ?: throw IllegalStateException("请先生成寻路地图")
                val parent = file.toPath().parent ?: Path.of(".")
                Files.createDirectories(parent)
                javax.imageio.ImageIO.write(image, extensionOf(file), file)
                val relativePath = relativizeForConfig(file.toPath())
                updateState { copy(mapImagePath = relativePath) }
                loadedConfig?.let { config ->
                    loadedConfig = AppConfig(
                        config.system(),
                        VisionConfig(
                            config.vision().windowTitle(),
                            relativePath,
                            config.vision().arrowTemplate(),
                            config.vision().miniMapRegion(),
                            config.vision().target(),
                            config.vision().matchAreaSize(),
                            config.vision().obstacleThreshold(),
                            config.vision().moveStep(),
                            config.vision().arriveDistance(),
                            config.vision().ocr(),
                            config.vision().yolo(),
                            buildMapPreprocessConfig(),
                            buildMapClosureConfig(),
                            buildNavigationConfig()
                        ),
                        config.uiAutomation(),
                        config.input()
                    )
                }
                visionNavigationService.clearAnalyzerMapCaches()
                navigationAnalyzer.clearMapCaches()
                appendLog("寻路地图已应用为导航 mapImage: $relativePath")
                setStatus("寻路地图已应用到导航配置")
            }
        }
    }

    fun captureGameWindow() {
        scope.launch {
            runAction("游戏窗口截图") {
                val config = buildVisionConfig()
                val window = windowService.findWindow(config.windowTitle())
                if (window.isEmpty) {
                    val message = "未找到窗口: ${config.windowTitle()}"
                    setStatus(message)
                    appendLog(message)
                    return@runAction
                }

                val windowRef = window.get()
                val capture = windowService.capture(windowRef.bounds())
                setSourceImage(capture, windowRef.bounds(), "window:${windowRef.title()}")
                updateState { copy(sourceMode = StudioSourceMode.WINDOW) }
                appendLog("已捕获窗口: ${windowRef.title()} ${rectText(windowRef.bounds())}")
                setStatus("游戏窗口截图完成")
            }
        }
    }

    fun processCurrentImage() {
        scope.launch {
            runAction("图片处理") {
                val image = currentSourceImage ?: run {
                    appendLog("请先加载示例、捕获窗口或选择本地图像。")
                    setStatus("先准备图像")
                    return@runAction
                }
                val imageToProcess = resolveProcessingSource(image)
                val sourceMat = ImageProcessor.bufferedImageToMat(imageToProcess)
                val gray = Mat()
                Imgproc.cvtColor(sourceMat, gray, Imgproc.COLOR_BGR2GRAY)
                val output = if (_state.value.binaryEnabled) {
                    val binary = Mat()
                    Imgproc.threshold(gray, binary, _state.value.threshold.toDouble(), 255.0, Imgproc.THRESH_BINARY)
                    binary
                } else {
                    gray
                }
                val processed = ImageProcessor.matToBufferedImage(output)
                updateState { copy(processedImage = processed) }
                appendLog("已处理图像: ${if (_state.value.binaryEnabled) "二值化处理" else "灰度处理"}")
                setStatus("图片处理完成")
            }
        }
    }

    fun analyzeCurrentImage(focusClickPreview: Boolean) {
        scope.launch {
            runAction("图像分析") {
                val image = currentSourceImage ?: run {
                    appendLog("请先加载示例、捕获窗口或选择本地图像。")
                    setStatus("先准备图像")
                    return@runAction
                }
                val config = buildVisionConfig()
                val analysis = if (_state.value.sourceMode == StudioSourceMode.MINIMAP) {
                    captureAnalysisService.analyzeMiniMap(config, image, currentSourceName)
                } else {
                    captureAnalysisService.analyzeWindowCapture(
                        config,
                        image,
                        currentSourceBounds ?: Rectangle(0, 0, image.width, image.height),
                        currentSourceName
                    )
                }
                applyCaptureAnalysis(analysis)
                updateState {
                    copy(selectedPreviewTab = if (focusClickPreview) PreviewTab.CLICK else PreviewTab.MAP)
                }
                setStatus("分析完成")
            }
        }
    }

    fun executeSingleStep() {
        scope.launch {
            runAction("单步导航执行") {
                val config = buildVisionConfig()
                val clickBackend = loadedConfig?.input()?.clickBackend() ?: com.auto.config.ClickBackend.WIN32
                val result = visionNavigationService.runOnceNow(config, _state.value.dryRun, clickBackend)
                appendLog("执行单步导航: result=$result, dryRun=${_state.value.dryRun}")
                setStatus(if (result) "单步导航执行完成" else "单步导航未生成有效点击")
            }
        }
    }

    fun startContinuousNavigation() {
        scope.launch {
            runAction("连续导航启动") {
                loadedConfig?.input()?.clickBackend()?.let { visionNavigationService.setClickBackend(it) }
                visionNavigationService.start({ buildVisionConfig() }, _state.value.dryRun)
                updateState { copy(navigationRunning = true) }
                appendLog("已启动连续自动导航（捕获→定位→A*→点击），dryRun=${_state.value.dryRun}")
                setStatus("连续导航已启动")
            }
        }
    }

    fun stopNavigation() {
        visionNavigationService.stop()
        updateState { copy(navigationRunning = false) }
        appendLog("已停止连续导航")
        setStatus("导航已停止")
    }

    fun runNavigationPreflight() {
        scope.launch {
            updateState { copy(preflightRunning = true, preflightSummary = "预检进行中…") }
            runAction("导航预检") {
                val config = buildVisionConfig()
                val clickBackend = loadedConfig?.input()?.clickBackend() ?: com.auto.config.ClickBackend.WIN32
                val interceptionHome = loadedConfig?.input()?.interceptionHome() ?: ""
                val result = navigationPreflightService.run(
                    config,
                    _state.value.dryRun,
                    clickBackend,
                    interceptionHome
                )
                val uiItems = result.items().map { item: com.auto.vision.PreflightItem ->
                    PreflightItemUi(
                        id = item.id(),
                        title = item.title(),
                        level = item.level().name,
                        levelLabel = item.level().label(),
                        detail = item.detail()
                    )
                }
                updateState {
                    copy(
                        preflightItems = uiItems,
                        preflightReady = result.ready(),
                        preflightSummary = result.summary(),
                        preflightRunning = false
                    )
                }
                appendLog("=== 导航预检 ===")
                appendLog(result.summary())
                uiItems.forEach { item: PreflightItemUi ->
                    appendLog("[${item.levelLabel}] ${item.title}: ${item.detail}")
                }
                setStatus(if (result.ready()) "预检通过，可开始连续导航" else "预检未通过，请查看下方明细")
            }
            updateState { copy(preflightRunning = false) }
        }
    }

    fun resetNavigationDebug() {
        navigationPipelineDebugger.reset()
        updateState {
            copy(
                navigationDebugSteps = defaultNavigationDebugSteps(),
                navigationDebugRunningStep = null,
                navigationDebugSessionDir = "",
                debugArtifactPreview = null,
                debugArtifactLabel = ""
            )
        }
        appendLog("寻路链路诊断已重置。")
    }

    fun showNavigationDebugArtifact(label: String, image: BufferedImage?) {
        if (image == null) return
        updateState {
            copy(
                debugArtifactPreview = image,
                debugArtifactLabel = label
            )
        }
    }

    fun clearNavigationDebugArtifactPreview() {
        updateState { copy(debugArtifactPreview = null, debugArtifactLabel = "") }
    }

    fun runNavigationDebugStep(step: NavigationDebugStep) {
        scope.launch {
            runAction("寻路诊断 ${step.label()}") {
                syncNavigationDebuggerCapture()
                val result = navigationPipelineDebugger.runStep(step, buildVisionConfig(), _state.value.dryRun)
                applyNavigationDebugResult(result)
            }
        }
    }

    fun runAllNavigationDebugSteps() {
        scope.launch {
            runAction("寻路链路逐步诊断") {
                resetNavigationDebug()
                syncNavigationDebuggerCapture()
                for (step in NavigationDebugStep.entries) {
                    updateState { copy(navigationDebugRunningStep = step) }
                    val result = navigationPipelineDebugger.runStep(step, buildVisionConfig(), _state.value.dryRun)
                    applyNavigationDebugResult(result)
                    if (!result.success()) {
                        appendLog("链路在步骤 ${step.order()}「${step.label()}」停止。")
                        break
                    }
                }
                updateState { copy(navigationDebugRunningStep = null) }
            }
        }
    }

    private fun syncNavigationDebuggerCapture() {
        currentSourceImage?.let { image ->
            navigationPipelineDebugger.adoptExistingCapture(
                image,
                currentSourceBounds,
                currentSourceName
            )
        }
    }

    private fun applyNavigationDebugResult(result: NavigationDebugStepResult) {
        if (result.step() == NavigationDebugStep.CAPTURE_WINDOW && result.success()) {
            navigationPipelineDebugger.getSourceImage()?.let { image: BufferedImage ->
                setSourceImage(
                    image,
                    navigationPipelineDebugger.getSourceBounds() ?: Rectangle(0, 0, image.width, image.height),
                    navigationPipelineDebugger.getSourceName()
                )
            }
        }
        result.analysis()?.let { navigation ->
            updateState {
                copy(
                    miniMapPreviewImage = navigation.miniMapPreviewImage(),
                    mapPreviewImage = navigation.mapPreviewImage(),
                    clickPreviewImage = navigation.clickPreviewImage(),
                    currentPointText = formatPoint(navigation.currentMapPoint()?.x, navigation.currentMapPoint()?.y),
                    lastNavigationMapPoint = navigation.currentMapPoint()?.let { it.x.toInt() to it.y.toInt() },
                    targetPointText = formatPoint(navigation.targetMapPoint()?.x, navigation.targetMapPoint()?.y),
                    nextClickText = formatPoint(navigation.nextScreenPoint()?.x, navigation.nextScreenPoint()?.y),
                    navigationConfidenceText = "%.2f".format(navigation.localizationConfidence())
                )
            }
            appendLog(navigation.debugSummary())
        }
        updateState {
            val artifactUi = result.artifacts().map { artifact ->
                NavigationDebugArtifactUi(
                    id = artifact.id(),
                    label = artifact.label(),
                    image = artifact.image()
                )
            }
            val firstArtifact = artifactUi.firstOrNull { it.image != null }
            copy(
                navigationDebugSteps = navigationDebugSteps.map { item ->
                    if (item.step == result.step()) {
                        item.copy(
                            success = result.success(),
                            message = result.message(),
                            artifacts = artifactUi,
                            artifactDir = result.artifactDir()
                        )
                    } else {
                        item
                    }
                },
                navigationDebugSessionDir = result.artifactDir().ifBlank { navigationDebugSessionDir },
                selectedPreviewTab = result.previewTab()?.let { tab ->
                    runCatching { PreviewTab.valueOf(tab.name) }.getOrNull()
                } ?: selectedPreviewTab,
                debugArtifactPreview = firstArtifact?.image ?: debugArtifactPreview,
                debugArtifactLabel = firstArtifact?.label ?: debugArtifactLabel,
                status = "步骤 ${result.step().order()} ${result.step().label()}: ${if (result.success()) "通过" else "失败"}"
            )
        }
        if (result.artifactDir().isNotBlank()) {
            appendLog("诊断图片已保存: ${result.artifactDir()}")
        }
        appendLog("[诊断 ${result.step().order()}] ${result.step().label()}: ${result.message()}")
    }

    private fun defaultNavigationDebugSteps(): List<NavigationDebugStepStatus> =
        NavigationDebugStep.entries.map { NavigationDebugStepStatus(it) }

    fun inspectUiAutomationWindow() {
        scope.launch {
            runAction("UI Automation 检查") {
                val config = buildUiAutomationConfig()
                val result = uiAutomationService.inspectWindow(_state.value.windowTitle.ifBlank { buildVisionConfig().windowTitle() }, config)
                updateState { copy(inspectedUiElements = result.elements()) }
                appendLog("UI Automation inspect: success=${result.success()}, message=${result.message()}, elements=${result.elements().size}")
                setStatus(result.message().ifBlank { if (result.success()) "UI Automation 检查完成" else "UI Automation 检查失败" })
            }
        }
    }

    fun runUiAutomationRules() {
        scope.launch {
            runAction("UI Automation 规则执行") {
                val config = buildUiAutomationConfig()
                val executions = uiAutomationService.executeRules(config)
                updateState { copy(uiAutomationExecutions = executions) }
                appendLog("UI Automation rules: ${executions.joinToString { "${it.ruleName()}:${it.success()}" }}")
                setStatus("UI Automation 规则执行完成")
            }
        }
    }

    fun selectPreviewTab(tab: PreviewTab) {
        updateState { copy(selectedPreviewTab = tab) }
    }

    fun updateWindowTitle(value: String) = updateState { copy(windowTitle = value) }
    fun updateSourceMode(value: StudioSourceMode) = updateState { copy(sourceMode = value) }
    fun updateDryRun(value: Boolean) = updateState { copy(dryRun = value) }
    fun updateBinaryEnabled(value: Boolean) = updateState { copy(binaryEnabled = value) }
    fun updateThreshold(value: Int) = updateState { copy(threshold = value) }
    fun updateMiniMapRegion(transform: RegionForm.() -> RegionForm) = updateState { copy(miniMapRegion = miniMapRegion.transform()) }

    fun startMiniMapRegionPick() {
        if (_state.value.sourceImage == null) {
            appendLog("请先捕获窗口或加载本地图像，再在源图上框选小地图。")
            setStatus("缺少源图")
            return
        }
        updateState {
            copy(
                regionPickTarget = RegionPickTarget.MINIMAP,
                selectedPreviewTab = PreviewTab.SOURCE
            )
        }
        setStatus("在源图上拖拽框选小地图区域")
    }

    fun startMapRegionPick() {
        if (_state.value.sourceImage == null) {
            appendLog("请先捕获窗口或加载本地图像，再在源图上框选地图区域。")
            setStatus("缺少源图")
            return
        }
        updateState {
            copy(
                regionPickTarget = RegionPickTarget.MAP,
                selectedPreviewTab = PreviewTab.SOURCE
            )
        }
        setStatus("在源图上拖拽框选地图区域")
    }

    fun cancelRegionPick() {
        updateState { copy(regionPickTarget = RegionPickTarget.NONE) }
        setStatus("已取消框选")
    }

    fun applyPickedRegion(x: Int, y: Int, width: Int, height: Int) {
        val target = _state.value.regionPickTarget
        if (target == RegionPickTarget.NONE) {
            return
        }
        val region = RegionForm(x.toString(), y.toString(), width.toString(), height.toString())
        when (target) {
            RegionPickTarget.MINIMAP -> {
                updateMiniMapRegion { region }
                persistMiniMapRegion(region.toConfig())
            }
            RegionPickTarget.MAP -> updateMapRegion { region }
            RegionPickTarget.NONE -> return
        }
        val label = when (target) {
            RegionPickTarget.MINIMAP -> "小地图 ROI"
            RegionPickTarget.MAP -> "地图 ROI"
            RegionPickTarget.NONE -> "区域"
        }
        updateState { copy(regionPickTarget = RegionPickTarget.NONE) }
        appendLog("已设置$label: ($x, $y, $width, $height)")
        setStatus("${label}已更新")
    }

    fun saveMiniMapRegionSettings() {
        persistMiniMapRegion(_state.value.miniMapRegion.toConfig())
        setStatus("小地图 ROI 已保存")
    }

    private fun persistMiniMapRegion(region: RegionConfig) {
        scope.launch {
            runCatching {
                configWriter.saveMiniMapRegion(region)
                appendLog("小地图 ROI 已保存到 ${AppConfigLoader.DEFAULT_RESOURCE}")
            }.onFailure { error ->
                appendLog("小地图 ROI 保存失败: ${error.message}")
            }
        }
    }
    fun updateTarget(transform: PointForm.() -> PointForm) = updateState { copy(target = target.transform()) }
    fun updateMatchAreaSize(value: String) = updateState { copy(matchAreaSize = value) }
    fun updateMoveStep(value: String) = updateState { copy(moveStep = value) }
    fun updateObstacleThreshold(value: String) = updateState { copy(obstacleThreshold = value) }
    fun updateArriveDistance(value: String) = updateState { copy(arriveDistance = value) }
    fun updateMapImagePath(value: String) = updateState { copy(mapImagePath = value) }
    fun updateMapRegion(transform: RegionForm.() -> RegionForm) = updateState { copy(mapPreprocess = mapPreprocess.copy(mapRegion = mapPreprocess.mapRegion.transform())) }
    fun updateMapPreprocess(transform: MapPreprocessForm.() -> MapPreprocessForm) = updateState { copy(mapPreprocess = mapPreprocess.transform()) }
    fun updateMapClosure(transform: MapClosureForm.() -> MapClosureForm) = updateState { copy(mapClosure = mapClosure.transform()) }
    fun updateNavigation(transform: NavigationForm.() -> NavigationForm) = updateState { copy(navigation = navigation.transform()) }

    fun setPendingCalibrationScreenPoint(x: Int, y: Int) {
        updateState { copy(pendingCalibrationScreenX = x.toString(), pendingCalibrationScreenY = y.toString()) }
    }

    fun setPendingCalibrationScreenX(value: String) = updateState { copy(pendingCalibrationScreenX = value) }

    fun setPendingCalibrationScreenY(value: String) = updateState { copy(pendingCalibrationScreenY = value) }

    fun addCalibrationPointFromCurrentMapLocation() {
        val mapPoint = _state.value.lastNavigationMapPoint ?: run {
            appendLog("请先执行分析，确保已获得当前地图坐标。")
            setStatus("缺少当前地图坐标")
            return
        }
        val screenX = _state.value.pendingCalibrationScreenX.toIntOrNull()
        val screenY = _state.value.pendingCalibrationScreenY.toIntOrNull()
        if (screenX == null || screenY == null) {
            appendLog("请先在点击预览上选点，或填写屏幕坐标。")
            setStatus("缺少屏幕标定坐标")
            return
        }
        updateNavigation {
            copy(
                screenCalibrationEnabled = true,
                calibrationPoints = calibrationPoints + ScreenCalibrationPointForm(
                    mapPoint.first.toString(),
                    mapPoint.second.toString(),
                    screenX.toString(),
                    screenY.toString()
                )
            )
        }
        appendLog("已添加标定点: map(${mapPoint.first}, ${mapPoint.second}) -> screen($screenX, $screenY)")
        setStatus("标定点已添加 (${_state.value.navigation.calibrationPoints.size} 个)")
    }

    fun clearCalibrationPoints() {
        updateNavigation { copy(calibrationPoints = emptyList(), screenCalibrationEnabled = false) }
        appendLog("已清空屏幕标定点。")
    }

    fun updateOcr(transform: OcrForm.() -> OcrForm) = updateState { copy(ocr = ocr.transform()) }
    fun updateOcrRegion(index: Int, transform: OcrRegionForm.() -> OcrRegionForm) = updateState {
        copy(ocr = ocr.copy(regions = ocr.regions.mapIndexed { currentIndex, region ->
            if (currentIndex == index) region.transform() else region
        }))
    }

    fun updateYolo(transform: YoloForm.() -> YoloForm) = updateState { copy(yolo = yolo.transform()) }
    fun updateUiAutomation(transform: UiAutomationForm.() -> UiAutomationForm) = updateState { copy(uiAutomation = uiAutomation.transform()) }

    fun close() {
        stopNavigation()
        visionNavigationService.close()
    }

    private fun applyConfig(config: AppConfig) {
        InterceptionBootstrap.configureHome(config.input().interceptionHome())
        visionNavigationService.setClickBackend(config.input().clickBackend())
        navigationPipelineDebugger.setClickBackend(config.input().clickBackend())
        currentSourceImage = null
        currentSourceBounds = null
        currentSourceName = "未加载图像"
        lastCaptureAnalysis = null
        updateState {
            copy(
                loadedConfig = config,
                windowTitle = config.vision().windowTitle(),
                sourceMode = StudioSourceMode.WINDOW,
                dryRun = config.system().dryRun(),
                miniMapRegion = RegionForm.from(config.vision().miniMapRegion()),
                target = PointForm.from(config.vision().target()),
                matchAreaSize = config.vision().matchAreaSize().toString(),
                moveStep = config.vision().moveStep().toString(),
                obstacleThreshold = config.vision().obstacleThreshold().toString(),
                arriveDistance = config.vision().arriveDistance().toString(),
                mapImagePath = config.vision().mapImage(),
                mapPreprocess = MapPreprocessForm.from(config.vision().mapPreprocess()),
                mapClosure = MapClosureForm.from(config.vision().mapClosure()),
                navigation = NavigationForm.from(config.vision().navigation()),
                ocr = OcrForm.from(config.vision().ocr()),
                yolo = YoloForm.from(config.vision().yolo()),
                uiAutomation = UiAutomationForm.from(config.uiAutomation()),
                status = "配置加载完成",
                sourceName = "未加载图像",
                boundsText = "",
                sourceImage = null,
                processedImage = null,
                miniMapPreviewImage = null,
                mapPreviewImage = null,
                clickPreviewImage = null,
                ocrOverlayImage = null,
                yoloOverlayImage = null,
                combinedPreviewImage = null,
                ocrResults = emptyList(),
                detections = emptyList(),
                uiAutomationExecutions = emptyList(),
                inspectedUiElements = emptyList(),
                currentPointText = "-",
                targetPointText = formatPoint(config.vision().target().x().toDouble(), config.vision().target().y().toDouble()),
                nextClickText = "-",
                mapOriginalImage = null,
                mapPathfindingImage = null,
                closureTestImage = null,
                closureTestImageName = "",
                mapClosureLeakPreview = null,
                mapClosureSealPreview = null,
                mapClosureClosed = null,
                mapClosureSummary = ""
            )
        }
    }

    private fun buildVisionConfig(): VisionConfig {
        val current = _state.value
        val baseVision = loadedConfig?.vision()
        return VisionConfig(
            current.windowTitle.trim(),
            current.mapImagePath.ifBlank { baseVision?.mapImage() ?: "img/sggd/largeMap_2.bmp" },
            baseVision?.arrowTemplate() ?: "img/arrow_template2.bmp",
            current.miniMapRegion.toConfig(),
            current.target.toConfig(),
            current.matchAreaSize.toIntStrict("匹配框"),
            current.obstacleThreshold.toDoubleStrict("障碍阈值"),
            current.moveStep.toIntStrict("移动步长"),
            current.arriveDistance.toDoubleStrict("到达距离"),
            current.ocr.toConfig(),
            current.yolo.toConfig(),
            buildMapPreprocessConfig(),
            buildMapClosureConfig(),
            buildNavigationConfig()
        )
    }

    private fun buildNavigationConfig(): NavigationConfig = _state.value.navigation.toConfig()

    private fun buildMapPreprocessConfig(): MapPreprocessConfig = _state.value.mapPreprocess.toConfig()

    private fun buildMapClosureConfig(): MapClosureConfig = _state.value.mapClosure.toConfig()

    private fun applyClosureAnalysis(
        analysis: com.auto.vision.PathfindingMapClosureAnalysis,
        previewTab: PreviewTab
    ) {
        updateState {
            copy(
                mapClosureLeakPreview = analysis.leakPreviewImage(),
                mapClosureSealPreview = analysis.sealedPreviewImage(),
                mapClosureClosed = analysis.closed(),
                mapClosureSummary = analysis.message(),
                selectedPreviewTab = previewTab,
                status = analysis.message()
            )
        }
        appendLog(analysis.message())
        if (analysis.hasWallEndpoints()) {
            appendLog("墙体端点 ${analysis.wallEndpointCount()} 个，预览中以紫色十字标出，请在此补墙。")
        } else if (analysis.hasLeakPoint()) {
            appendLog("关注位置: (${analysis.leakX()}, ${analysis.leakY()})，预览中已标红叉。")
        } else if (!analysis.closed()) {
            appendLog("提示：可导出手动补墙，或在右侧预览密封效果。")
        }
    }

    private fun applyMapPreprocess(source: BufferedImage, fromOriginalOnly: Boolean = false) {
        val baseConfig = buildMapPreprocessConfig()
        val config = if (fromOriginalOnly) {
            MapPreprocessConfig(
                RegionConfig(0, 0, source.width, source.height),
                baseConfig.binaryEnabled(),
                baseConfig.threshold(),
                baseConfig.useOtsu(),
                baseConfig.removeOrangeMarkers(),
                baseConfig.orangeHueMin(),
                baseConfig.orangeHueMax(),
                baseConfig.orangeSatMin(),
                baseConfig.orangeValMin(),
                baseConfig.minTemplateScore(),
                baseConfig.minTemplateScoreGap(),
                baseConfig.alignmentUseEdges()
            )
        } else {
            baseConfig
        }
        val result = pathfindingMapPreprocessor.process(source, config)
        if (!result.success()) {
            appendLog(result.message())
            setStatus(result.message())
            return
        }
        updateState {
            copy(
                mapOriginalImage = result.originalMapImage(),
                mapPathfindingImage = result.pathfindingMapImage(),
                selectedPreviewTab = PreviewTab.MAP_PATHFINDING,
                status = result.message()
            )
        }
        appendLog(result.message())
    }

    private fun readLocalImage(file: File): BufferedImage {
        if (!file.isFile) {
            throw IllegalArgumentException("文件不存在: ${file.absolutePath}")
        }
        val image = javax.imageio.ImageIO.read(file)
            ?: throw IllegalArgumentException("不支持的图片格式，请使用 BMP/PNG/JPG: ${file.name}")
        return image
    }

    private fun extensionOf(file: File): String {
        val name = file.name
        val dot = name.lastIndexOf('.')
        return if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1) else "bmp"
    }

    private fun relativizeForConfig(path: Path): String {
        val cwd = Path.of("").toAbsolutePath().normalize()
        val normalized = path.toAbsolutePath().normalize()
        return if (normalized.startsWith(cwd)) {
            cwd.relativize(normalized).toString().replace('\\', '/')
        } else {
            normalized.toString()
        }
    }

    private fun buildUiAutomationConfig(): UiAutomationConfig {
        return _state.value.uiAutomation.toConfig()
    }

    private fun applyCaptureAnalysis(captureAnalysis: CaptureAnalysis) {
        lastCaptureAnalysis = captureAnalysis
        val navigation = captureAnalysis.navigationAnalysis()
        currentSourceImage = captureAnalysis.sourceImage() ?: navigation.sourceImage()
        currentSourceBounds = currentSourceBounds ?: currentSourceImage?.let { Rectangle(0, 0, it.width, it.height) }
        currentSourceName = captureAnalysis.sourceName().ifBlank { navigation.sourceName() }
        updateState {
            copy(
                sourceName = currentSourceName,
                boundsText = currentSourceBounds?.let(::rectText) ?: "",
                sourceImage = currentSourceImage,
                miniMapPreviewImage = navigation.miniMapPreviewImage(),
                mapPreviewImage = navigation.mapPreviewImage(),
                clickPreviewImage = navigation.clickPreviewImage(),
                ocrOverlayImage = overlayOcr(currentSourceImage, captureAnalysis.ocrResults()),
                yoloOverlayImage = overlayDetections(currentSourceImage, captureAnalysis.detections()),
                combinedPreviewImage = captureAnalysis.combinedPreviewImage(),
                ocrResults = captureAnalysis.ocrResults(),
                detections = captureAnalysis.detections(),
                currentPointText = formatPoint(navigation.currentMapPoint()?.x, navigation.currentMapPoint()?.y),
                lastNavigationMapPoint = navigation.currentMapPoint()?.let { it.x.toInt() to it.y.toInt() },
                targetPointText = formatPoint(navigation.targetMapPoint()?.x, navigation.targetMapPoint()?.y),
                nextClickText = formatPoint(navigation.nextScreenPoint()?.x, navigation.nextScreenPoint()?.y),
                status = navigation.message()
            )
        }
        appendLog(navigation.debugSummary())
        captureAnalysis.diagnostics().forEach(::appendLog)
        captureAnalysis.ocrResults().forEach { result ->
            appendLog("OCR ${result.name()}: '${result.text()}' conf=${"%.1f".format(result.confidence())} bounds=${rectText(result.bounds())}")
        }
        captureAnalysis.detections().forEach { detection ->
            appendLog(
                "YOLO ${detection.label()}: score=${"%.2f".format(detection.score())} bounds=${rectText(detection.bounds())}"
            )
        }
    }

    private fun resolveProcessingSource(sourceImage: BufferedImage): BufferedImage {
        if (_state.value.sourceMode == StudioSourceMode.MINIMAP) {
            return sourceImage
        }
        val region = _state.value.miniMapRegion.toConfig()
        val miniMap = ImageProcessor.extractRegion(
            ImageProcessor.bufferedImageToMat(sourceImage),
            org.opencv.core.Rect(region.x(), region.y(), region.width(), region.height())
        )
        return ImageProcessor.matToBufferedImage(miniMap)
    }

    private fun setSourceImage(image: BufferedImage, bounds: Rectangle, name: String) {
        currentSourceImage = image
        currentSourceBounds = Rectangle(bounds)
        currentSourceName = name
        updateState {
            copy(
                sourceImage = image,
                sourceName = name,
                boundsText = rectText(bounds),
                processedImage = null,
                miniMapPreviewImage = null,
                mapPreviewImage = null,
                clickPreviewImage = null,
                ocrOverlayImage = null,
                yoloOverlayImage = null,
                combinedPreviewImage = null,
                ocrResults = emptyList(),
                detections = emptyList(),
                inspectedUiElements = emptyList(),
                currentPointText = "-",
                nextClickText = "-"
            )
        }
    }

    private fun overlayOcr(source: BufferedImage?, results: List<com.auto.ocr.OcrResult>): BufferedImage? {
        if (source == null) {
            return null
        }
        val image = ImageProcessor.copyBufferedImage(source)
        val graphics = image.createGraphics()
        configureOverlayGraphics(graphics, Color(0, 220, 120))
        results.forEach { result ->
            val bounds = result.bounds()
            graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height)
            graphics.drawString("${result.name()}: ${result.text()}", bounds.x, maxOf(14, bounds.y - 6))
        }
        graphics.dispose()
        return image
    }

    private fun overlayDetections(source: BufferedImage?, detections: List<DetectedObject>): BufferedImage? {
        if (source == null) {
            return null
        }
        val image = ImageProcessor.copyBufferedImage(source)
        val graphics = image.createGraphics()
        configureOverlayGraphics(graphics, Color(255, 180, 40))
        detections.forEach { detection ->
            val bounds = detection.bounds()
            graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height)
            graphics.drawString("${detection.label()} ${"%.2f".format(detection.score())}", bounds.x, maxOf(14, bounds.y - 6))
        }
        graphics.dispose()
        return image
    }

    private fun configureOverlayGraphics(graphics: Graphics2D, color: Color) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = color
        graphics.stroke = BasicStroke(3f)
    }

    private fun appendLog(message: String) {
        updateState {
            copy(logs = logs + "[${LocalTime.now().format(timeFormat)}] $message")
        }
    }

    private fun setStatus(message: String) {
        updateState { copy(status = message) }
    }

    private inline fun runAction(operationName: String, action: () -> Unit) {
        try {
            action()
        } catch (e: RuntimeException) {
            appendLog("${operationName}失败: ${e.message}")
            setStatus("${operationName}失败")
        }
    }

    private fun formatNavigationRuntime(status: NavigationRuntimeStatus): String =
        "${status.state()} / ${status.lastAction()} / 路点 ${status.waypointIndex()} / ${status.message()}"

    private fun formatLocalizationPoint(status: NavigationRuntimeStatus): String {
        val accepted = status.localizationAcceptedPoint()
        val raw = status.localizationRawPoint()
        if (accepted == null) {
            return "-"
        }
        val acceptedText = formatPoint(accepted.x, accepted.y)
        if (raw == null || !status.localizationOutlierRejected()) {
            return acceptedText
        }
        return "$acceptedText（原始 ${formatPoint(raw.x, raw.y)}，已剔除）"
    }

    private fun updateState(transform: StudioUiState.() -> StudioUiState) {
        _state.update(transform)
    }

    private fun rectText(rect: Rectangle): String = "(${rect.x}, ${rect.y}, ${rect.width}, ${rect.height})"

    private fun regionText(region: RegionConfig): String =
        "(${region.x()}, ${region.y()}, ${region.width()}, ${region.height()})"
}

enum class RegionPickTarget {
    NONE,
    MINIMAP,
    MAP
}

enum class StudioSourceMode {
    WINDOW,
    MINIMAP
}

enum class PreviewTab(val label: String) {
    SOURCE("源图"),
    MAP_ORIGINAL("地图原图"),
    MAP_PATHFINDING("寻路地图"),
    MAP_CLOSURE_INPUT("检测原图"),
    MAP_CLOSURE_LEAK("闭合检测"),
    MAP_CLOSURE_SEAL("密封预览"),
    PROCESSED("处理结果"),
    MINIMAP("小地图分析"),
    MAP("地图路径"),
    CLICK("点击预览"),
    OCR("OCR 叠加"),
    YOLO("YOLO 叠加"),
    COMBINED("综合预览")
}

data class StudioUiState(
    val loadedConfig: AppConfig? = null,
    val sourceMode: StudioSourceMode = StudioSourceMode.WINDOW,
    val selectedPreviewTab: PreviewTab = PreviewTab.SOURCE,
    val dryRun: Boolean = true,
    val binaryEnabled: Boolean = true,
    val threshold: Int = 127,
    val windowTitle: String = "",
    val miniMapRegion: RegionForm = RegionForm(),
    val target: PointForm = PointForm("779", "285"),
    val matchAreaSize: String = "100",
    val moveStep: String = "80",
    val obstacleThreshold: String = "200.0",
    val arriveDistance: String = "10.0",
    val mapImagePath: String = "img/sggd/largeMap_2.bmp",
    val mapPreprocess: MapPreprocessForm = MapPreprocessForm(),
    val mapClosure: MapClosureForm = MapClosureForm(),
    val navigation: NavigationForm = NavigationForm(),
    val pendingCalibrationScreenX: String = "",
    val pendingCalibrationScreenY: String = "",
    val lastNavigationMapPoint: Pair<Int, Int>? = null,
    val navigationRuntimeText: String = "-",
    val navigationWaypointIndex: Int = 0,
    val navigationConfidenceText: String = "-",
    val localizationDetailText: String = "",
    val regionPickTarget: RegionPickTarget = RegionPickTarget.NONE,
    val mapOriginalImage: BufferedImage? = null,
    val mapPathfindingImage: BufferedImage? = null,
    val closureTestImage: BufferedImage? = null,
    val closureTestImageName: String = "",
    val mapClosureLeakPreview: BufferedImage? = null,
    val mapClosureSealPreview: BufferedImage? = null,
    val mapClosureClosed: Boolean? = null,
    val mapClosureSummary: String = "",
    val ocr: OcrForm = OcrForm(),
    val yolo: YoloForm = YoloForm(),
    val uiAutomation: UiAutomationForm = UiAutomationForm(),
    val status: String = "就绪",
    val sourceName: String = "未加载图像",
    val boundsText: String = "",
    val sourceImage: BufferedImage? = null,
    val processedImage: BufferedImage? = null,
    val miniMapPreviewImage: BufferedImage? = null,
    val mapPreviewImage: BufferedImage? = null,
    val clickPreviewImage: BufferedImage? = null,
    val ocrOverlayImage: BufferedImage? = null,
    val yoloOverlayImage: BufferedImage? = null,
    val combinedPreviewImage: BufferedImage? = null,
    val ocrResults: List<com.auto.ocr.OcrResult> = emptyList(),
    val detections: List<DetectedObject> = emptyList(),
    val inspectedUiElements: List<UiAutomationElement> = emptyList(),
    val uiAutomationExecutions: List<UiAutomationRuleExecution> = emptyList(),
    val currentPointText: String = "-",
    val targetPointText: String = "-",
    val nextClickText: String = "-",
    val logs: List<String> = emptyList(),
    val navigationRunning: Boolean = false,
    val navigationDebugSteps: List<NavigationDebugStepStatus> = NavigationDebugStep.entries.map { NavigationDebugStepStatus(it) },
    val navigationDebugRunningStep: NavigationDebugStep? = null,
    val navigationDebugSessionDir: String = "",
    val debugArtifactPreview: BufferedImage? = null,
    val debugArtifactLabel: String = "",
    val preflightRunning: Boolean = false,
    val preflightReady: Boolean? = null,
    val preflightSummary: String = "",
    val preflightItems: List<PreflightItemUi> = emptyList()
)

data class PreflightItemUi(
    val id: String,
    val title: String,
    val level: String,
    val levelLabel: String,
    val detail: String
)

data class NavigationDebugArtifactUi(
    val id: String,
    val label: String,
    val image: BufferedImage?
)

data class NavigationDebugStepStatus(
    val step: NavigationDebugStep,
    val success: Boolean? = null,
    val message: String = "",
    val artifacts: List<NavigationDebugArtifactUi> = emptyList(),
    val artifactDir: String = ""
)

data class MapPreprocessForm(
    val mapRegion: RegionForm = RegionForm("0", "0", "1600", "900"),
    val binaryEnabled: Boolean = true,
    val threshold: Int = 127,
    val useOtsu: Boolean = false,
    val removeOrangeMarkers: Boolean = true,
    val orangeHueMin: String = "10",
    val orangeHueMax: String = "35",
    val orangeSatMin: String = "80",
    val orangeValMin: String = "80",
    val minTemplateScore: String = "0.48",
    val minTemplateScoreGap: String = "0.08",
    val alignmentUseEdges: Boolean = true
) {
    fun toConfig(): MapPreprocessConfig = MapPreprocessConfig(
        mapRegion.toConfig(),
        binaryEnabled,
        threshold,
        useOtsu,
        removeOrangeMarkers,
        orangeHueMin.toIntStrict("橙色 Hue 下限"),
        orangeHueMax.toIntStrict("橙色 Hue 上限"),
        orangeSatMin.toIntStrict("橙色 Sat 下限"),
        orangeValMin.toIntStrict("橙色 Val 下限"),
        minTemplateScore.toDoubleStrict("模板最低分"),
        minTemplateScoreGap.toDoubleStrict("模板歧义阈值"),
        alignmentUseEdges
    )

    companion object {
        fun from(config: MapPreprocessConfig): MapPreprocessForm = MapPreprocessForm(
            mapRegion = RegionForm.from(config.mapRegion()),
            binaryEnabled = config.binaryEnabled(),
            threshold = config.threshold(),
            useOtsu = config.useOtsu(),
            removeOrangeMarkers = config.removeOrangeMarkers(),
            orangeHueMin = config.orangeHueMin().toString(),
            orangeHueMax = config.orangeHueMax().toString(),
            orangeSatMin = config.orangeSatMin().toString(),
            orangeValMin = config.orangeValMin().toString(),
            minTemplateScore = config.minTemplateScore().toString(),
            minTemplateScoreGap = config.minTemplateScoreGap().toString(),
            alignmentUseEdges = config.alignmentUseEdges()
        )
    }
}

data class ScreenCalibrationPointForm(
    val mapX: String,
    val mapY: String,
    val screenX: String,
    val screenY: String
) {
    fun toConfig(): ScreenCalibrationPointConfig = ScreenCalibrationPointConfig(
        mapX.toDoubleStrict("标定 mapX"),
        mapY.toDoubleStrict("标定 mapY"),
        screenX.toDoubleStrict("标定 screenX"),
        screenY.toDoubleStrict("标定 screenY")
    )

    companion object {
        fun from(config: ScreenCalibrationPointConfig): ScreenCalibrationPointForm = ScreenCalibrationPointForm(
            config.mapX().toString(),
            config.mapY().toString(),
            config.screenX().toString(),
            config.screenY().toString()
        )
    }
}

data class NavigationForm(
    val tickIntervalMs: String = "400",
    val stuckTimeoutMs: String = "3000",
    val stuckDistanceThreshold: String = "8.0",
    val waypointReachDistance: String = "15.0",
    val maxStuckRetries: String = "3",
    val minLocalizationConfidence: String = "0.45",
    val localizationSmoothingAlpha: String = "0.35",
    val localizationMaxPredictFrames: String = "2",
    val localizationOutlierRejectionEnabled: Boolean = true,
    val maxLocalizationJumpPx: String = "0",
    val screenCalibrationEnabled: Boolean = false,
    val calibrationPoints: List<ScreenCalibrationPointForm> = emptyList()
) {
    fun toConfig(): NavigationConfig = NavigationConfig(
        tickIntervalMs.toIntStrict("tickIntervalMs"),
        stuckTimeoutMs.toIntStrict("stuckTimeoutMs"),
        stuckDistanceThreshold.toDoubleStrict("stuckDistanceThreshold"),
        waypointReachDistance.toDoubleStrict("waypointReachDistance"),
        maxStuckRetries.toIntStrict("maxStuckRetries"),
        minLocalizationConfidence.toDoubleStrict("minLocalizationConfidence"),
        localizationSmoothingAlpha.toDoubleStrict("localizationSmoothingAlpha"),
        localizationMaxPredictFrames.toIntStrict("localizationMaxPredictFrames"),
        localizationOutlierRejectionEnabled,
        maxLocalizationJumpPx.toDoubleStrict("maxLocalizationJumpPx"),
        ScreenCalibrationConfig(
            screenCalibrationEnabled && calibrationPoints.size >= 3,
            calibrationPoints.map { it.toConfig() }
        )
    )

    companion object {
        fun from(config: NavigationConfig): NavigationForm = NavigationForm(
            tickIntervalMs = config.tickIntervalMs().toString(),
            stuckTimeoutMs = config.stuckTimeoutMs().toString(),
            stuckDistanceThreshold = config.stuckDistanceThreshold().toString(),
            waypointReachDistance = config.waypointReachDistance().toString(),
            maxStuckRetries = config.maxStuckRetries().toString(),
            minLocalizationConfidence = config.minLocalizationConfidence().toString(),
            localizationSmoothingAlpha = config.localizationSmoothingAlpha().toString(),
            localizationMaxPredictFrames = config.localizationMaxPredictFrames().toString(),
            localizationOutlierRejectionEnabled = config.localizationOutlierRejectionEnabled(),
            maxLocalizationJumpPx = config.maxLocalizationJumpPx().toString(),
            screenCalibrationEnabled = config.screenCalibration().enabled(),
            calibrationPoints = config.screenCalibration().points().map(ScreenCalibrationPointForm::from)
        )
    }
}

data class MapClosureForm(
    val walkableThreshold: String = "200",
    val sealExterior: Boolean = true,
    val sealBorderWidth: String = "2",
    val morphCloseKernelSize: String = "3"
) {
    fun toConfig(): MapClosureConfig = MapClosureConfig(
        walkableThreshold.toDoubleStrict("可走阈值"),
        sealExterior,
        sealBorderWidth.toIntStrict("边缘密封宽度"),
        morphCloseKernelSize.toIntStrict("墙缝闭合核")
    )

    companion object {
        fun from(config: MapClosureConfig): MapClosureForm = MapClosureForm(
            walkableThreshold = config.walkableThreshold().toString(),
            sealExterior = config.sealExterior(),
            sealBorderWidth = config.sealBorderWidth().toString(),
            morphCloseKernelSize = config.morphCloseKernelSize().toString()
        )
    }
}

private fun manualEditHint(): String =
    "手动编辑说明：白色=障碍/墙，黑色=可走。用白色画笔补全箭头、NPC 图标在边界上留下的缺口，" +
        "勿把大面积可走区涂白。编辑后保存为 BMP/PNG，再点「导入已编辑寻路地图」。"

data class RegionForm(
    val x: String = "0",
    val y: String = "0",
    val width: String = "200",
    val height: String = "200"
) {
    fun toConfig(): RegionConfig = RegionConfig(
        x.toIntStrict("区域 X"),
        y.toIntStrict("区域 Y"),
        width.toIntStrict("区域宽度"),
        height.toIntStrict("区域高度")
    )

    companion object {
        fun from(region: RegionConfig): RegionForm = RegionForm(
            region.x().toString(),
            region.y().toString(),
            region.width().toString(),
            region.height().toString()
        )
    }
}

data class PointForm(
    val x: String = "0",
    val y: String = "0"
) {
    fun toConfig(): PointConfig = PointConfig(x.toIntStrict("目标 X"), y.toIntStrict("目标 Y"))

    companion object {
        fun from(point: PointConfig): PointForm = PointForm(point.x().toString(), point.y().toString())
    }
}

data class OcrRegionForm(
    val name: String,
    val source: OcrRegionSource,
    val region: RegionForm,
    val scale: String,
    val threshold: String,
    val whitelist: String
) {
    fun toConfig(): OcrRegionConfig = OcrRegionConfig(
        name,
        source,
        region.toConfig(),
        scale.toDoubleStrict("OCR scale"),
        threshold.takeIf { it.isNotBlank() }?.toIntStrict("OCR threshold"),
        whitelist
    )

    companion object {
        fun from(config: OcrRegionConfig): OcrRegionForm = OcrRegionForm(
            config.name(),
            config.source(),
            RegionForm.from(config.region()),
            config.scale().toString(),
            config.threshold()?.toString() ?: "",
            config.whitelist()
        )
    }
}

data class OcrForm(
    val enabled: Boolean = false,
    val language: String = "eng",
    val pageSegMode: String = "7",
    val defaultWhitelist: String = "",
    val regions: List<OcrRegionForm> = emptyList()
) {
    fun toConfig(): OcrConfig = OcrConfig(
        enabled,
        language.ifBlank { "eng" },
        pageSegMode.toIntStrict("OCR pageSegMode"),
        defaultWhitelist,
        regions.map { it.toConfig() }
    )

    companion object {
        fun from(config: OcrConfig): OcrForm = OcrForm(
            config.enabled(),
            config.language(),
            config.pageSegMode().toString(),
            config.defaultWhitelist(),
            config.regions().map(OcrRegionForm::from)
        )
    }
}

data class YoloForm(
    val enabled: Boolean = false,
    val modelPath: String = "",
    val labelsPath: String = "",
    val inputWidth: String = "640",
    val inputHeight: String = "640",
    val confidenceThreshold: String = "0.25",
    val iouThreshold: String = "0.45",
    val maxDetections: String = "50",
    val regionEnabled: Boolean = false,
    val region: RegionForm = RegionForm("560", "260", "780", "500"),
    val classesOfInterest: String = ""
) {
    fun toConfig(): YoloConfig = YoloConfig(
        enabled,
        modelPath,
        labelsPath,
        inputWidth.toIntStrict("YOLO 输入宽度"),
        inputHeight.toIntStrict("YOLO 输入高度"),
        confidenceThreshold.toDoubleStrict("YOLO 置信度"),
        iouThreshold.toDoubleStrict("YOLO IOU"),
        maxDetections.toIntStrict("YOLO 最大检测数"),
        if (regionEnabled) region.toConfig() else null,
        classesOfInterest.split(",").map(String::trim).filter(String::isNotBlank)
    )

    companion object {
        fun from(config: YoloConfig): YoloForm = YoloForm(
            config.enabled(),
            config.modelPath(),
            config.labelsPath(),
            config.inputWidth().toString(),
            config.inputHeight().toString(),
            config.confidenceThreshold().toString(),
            config.iouThreshold().toString(),
            config.maxDetections().toString(),
            config.region() != null,
            config.region()?.let(RegionForm::from) ?: RegionForm("560", "260", "780", "500"),
            config.classesOfInterest().joinToString(", ")
        )
    }
}

data class UiAutomationActionForm(
    val controlType: String,
    val name: String,
    val automationId: String,
    val pattern: UiAutomationPattern,
    val value: String
) {
    fun toConfig(): UiAutomationActionConfig = UiAutomationActionConfig(controlType, name, automationId, pattern, value)

    companion object {
        fun from(config: UiAutomationActionConfig): UiAutomationActionForm = UiAutomationActionForm(
            config.controlType(),
            config.name(),
            config.automationId(),
            config.pattern(),
            config.value()
        )
    }
}

data class UiAutomationRuleForm(
    val name: String,
    val windowTitleContains: String,
    val actions: List<UiAutomationActionForm>
) {
    fun toConfig(): UiAutomationRuleConfig = UiAutomationRuleConfig(name, windowTitleContains, actions.map { it.toConfig() })

    companion object {
        fun from(config: UiAutomationRuleConfig): UiAutomationRuleForm = UiAutomationRuleForm(
            config.name(),
            config.windowTitleContains(),
            config.actions().map(UiAutomationActionForm::from)
        )
    }
}

data class UiAutomationForm(
    val enabled: Boolean = false,
    val helperCommand: String = "powershell -ExecutionPolicy Bypass -File tools/windows-ui-automation-helper.ps1",
    val timeoutMs: String = "10000",
    val rules: List<UiAutomationRuleForm> = emptyList()
) {
    fun toConfig(): UiAutomationConfig = UiAutomationConfig(
        enabled,
        helperCommand.split(" ").map(String::trim).filter(String::isNotBlank),
        timeoutMs.toIntStrict("UI Automation timeoutMs"),
        rules.map { it.toConfig() }
    )

    companion object {
        fun from(config: UiAutomationConfig): UiAutomationForm = UiAutomationForm(
            config.enabled(),
            config.helperCommand().joinToString(" "),
            config.timeoutMs().toString(),
            config.rules().map(UiAutomationRuleForm::from)
        )
    }
}

private fun String.toIntStrict(fieldName: String): Int =
    trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        ?: throw IllegalArgumentException("$fieldName 必须是整数")

private fun String.toDoubleStrict(fieldName: String): Double =
    trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        ?: throw IllegalArgumentException("$fieldName 必须是数字")

private fun formatPoint(x: Double?, y: Double?): String {
    if (x == null || y == null) {
        return "-"
    }
    return "(%.1f, %.1f)".format(x, y)
}
