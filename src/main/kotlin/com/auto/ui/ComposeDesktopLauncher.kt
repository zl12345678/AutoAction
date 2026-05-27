package com.auto.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.auto.ocr.OcrResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage

private const val SHOW_OCR_AND_YOLO_UI = false

object ComposeDesktopLauncher {
    @JvmStatic
    fun launch() {
        com.auto.window.WindowsDpi.enable()
        launchApp()
    }

    private fun launchApp() = application {
        val viewModel = remember { StudioViewModel() }
        DisposableEffect(viewModel) {
            onDispose { viewModel.close() }
        }
        Window(
            onCloseRequest = {
                viewModel.close()
                exitApplication()
            },
            title = "AutoAction Studio"
        ) {
            MaterialTheme(colorScheme = studioColors()) {
                AutoActionStudioApp(viewModel)
            }
        }
    }
}

@Composable
private fun AutoActionStudioApp(viewModel: StudioViewModel) {
    val state by viewModel.state.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LeftRail(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.fillMaxHeight().weight(0.30f)
        )
        PreviewPane(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.fillMaxHeight().weight(0.38f)
        )
        RightRail(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.fillMaxHeight().weight(0.32f)
        )
    }
}

private enum class LeftRailTab(val label: String) {
    PROJECT("项目"),
    VISION("视觉"),
    CAPTURE("截图"),
    MAP("寻路地图"),
    CLOSURE("闭合检测"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeftRail(state: StudioUiState, viewModel: StudioViewModel, modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(LeftRailTab.PROJECT) }
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = LeftRailTab.entries.indexOf(selectedTab),
                modifier = Modifier.fillMaxWidth()
            ) {
                LeftRailTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedTab) {
                    LeftRailTab.PROJECT -> LeftRailProjectTab(state, viewModel)
                    LeftRailTab.VISION -> LeftRailVisionTab(state, viewModel)
                    LeftRailTab.CAPTURE -> LeftRailCaptureTab(viewModel)
                    LeftRailTab.MAP -> LeftRailMapTab(state, viewModel)
                    LeftRailTab.CLOSURE -> LeftRailClosureTab(state, viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeftRailProjectTab(state: StudioUiState, viewModel: StudioViewModel) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("配置、截图、分析和执行都在一个工作台里跑。先拉样例，再切到真实窗口会比较顺手。", color = mutedText())
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::loadConfig) { Text("重新加载配置") }
                Button(onClick = viewModel::runSampleAnalysis) { Text("运行示例") }
                Button(onClick = viewModel::runNavigationPreflight) { Text("一键预检") }
            }
            if (state.preflightSummary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                PreflightSummary(state)
            }
        }
}

@Composable
private fun PreflightSummary(state: StudioUiState) {
    val summaryColor = when {
        state.preflightRunning -> MaterialTheme.colorScheme.onSurfaceVariant
        state.preflightReady == true -> Color(0xFF2E7D32)
        state.preflightReady == false -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        state.preflightSummary,
        color = summaryColor,
        style = MaterialTheme.typography.bodySmall
    )
    if (state.preflightItems.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.preflightItems.forEach { item ->
                PreflightItemRow(item)
            }
        }
    }
}

@Composable
private fun PreflightItemRow(item: PreflightItemUi) {
    val color = when (item.level) {
        "OK" -> Color(0xFF2E7D32)
        "WARN" -> Color(0xFFF57C00)
        else -> MaterialTheme.colorScheme.error
    }
    Text(
        "[${item.levelLabel}] ${item.title} — ${item.detail}",
        color = color,
        style = MaterialTheme.typography.bodySmall
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeftRailVisionTab(state: StudioUiState, viewModel: StudioViewModel) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledField("窗口标题", state.windowTitle, viewModel::updateWindowTitle)
            Text("输入源", style = MaterialTheme.typography.labelMedium, color = mutedText())
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceChip("窗口截图", selected = state.sourceMode == StudioSourceMode.WINDOW) {
                    viewModel.updateSourceMode(StudioSourceMode.WINDOW)
                }
                SourceChip("小地图", selected = state.sourceMode == StudioSourceMode.MINIMAP) {
                    viewModel.updateSourceMode(StudioSourceMode.MINIMAP)
                }
            }
            Spacer(Modifier.height(8.dp))
            LabeledCheckbox("Dry Run", state.dryRun, viewModel::updateDryRun)
            if (state.dryRun) {
                Text(
                    "Dry Run 开启：步骤 8 不会真实点击，也不会聚焦游戏。测试点击前请关闭。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            RegionEditor("小地图 ROI", state.miniMapRegion) { updatedRegion: RegionForm ->
                viewModel.updateMiniMapRegion { updatedRegion }
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = viewModel::startMiniMapRegionPick, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.regionPickTarget == RegionPickTarget.MINIMAP) "正在框选小地图…" else "在源图上框选小地图")
            }
            Text(
                "框选完成后会自动保存到 autoActionConfig.json，下次启动自动加载。",
                color = mutedText(),
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = viewModel::saveMiniMapRegionSettings, modifier = Modifier.fillMaxWidth()) {
                Text("保存小地图 ROI")
            }
            Spacer(Modifier.height(8.dp))
            PointEditor("目标点", state.target) { updatedPoint: PointForm ->
                viewModel.updateTarget { updatedPoint }
            }
            Spacer(Modifier.height(8.dp))
            TwoFieldRow(
                leftLabel = "匹配框",
                leftValue = state.matchAreaSize,
                onLeftChange = viewModel::updateMatchAreaSize,
                rightLabel = "移动步长",
                rightValue = state.moveStep,
                onRightChange = viewModel::updateMoveStep
            )
            Spacer(Modifier.height(8.dp))
            TwoFieldRow(
                leftLabel = "障碍阈值",
                leftValue = state.obstacleThreshold,
                onLeftChange = viewModel::updateObstacleThreshold,
                rightLabel = "到达距离",
                rightValue = state.arriveDistance,
                onRightChange = viewModel::updateArriveDistance
            )
            Spacer(Modifier.height(8.dp))
            Text("导航闭环", style = MaterialTheme.typography.labelMedium, color = mutedText())
            TwoFieldRow(
                leftLabel = "Tick(ms)",
                leftValue = state.navigation.tickIntervalMs,
                onLeftChange = { value: String -> viewModel.updateNavigation { copy(tickIntervalMs = value) } },
                rightLabel = "卡住超时(ms)",
                rightValue = state.navigation.stuckTimeoutMs,
                onRightChange = { value: String -> viewModel.updateNavigation { copy(stuckTimeoutMs = value) } }
            )
            Spacer(Modifier.height(6.dp))
            TwoFieldRow(
                leftLabel = "卡住距离",
                leftValue = state.navigation.stuckDistanceThreshold,
                onLeftChange = { value: String -> viewModel.updateNavigation { copy(stuckDistanceThreshold = value) } },
                rightLabel = "路点到达距离",
                rightValue = state.navigation.waypointReachDistance,
                onRightChange = { value: String -> viewModel.updateNavigation { copy(waypointReachDistance = value) } }
            )
            Spacer(Modifier.height(6.dp))
            TwoFieldRow(
                leftLabel = "最低置信度",
                leftValue = state.navigation.minLocalizationConfidence,
                onLeftChange = { value: String -> viewModel.updateNavigation { copy(minLocalizationConfidence = value) } },
                rightLabel = "卡住重试",
                rightValue = state.navigation.maxStuckRetries,
                onRightChange = { value: String -> viewModel.updateNavigation { copy(maxStuckRetries = value) } }
            )
            Spacer(Modifier.height(6.dp))
            TwoFieldRow(
                leftLabel = "平滑系数",
                leftValue = state.navigation.localizationSmoothingAlpha,
                onLeftChange = { value: String -> viewModel.updateNavigation { copy(localizationSmoothingAlpha = value) } },
                rightLabel = "预测帧数",
                rightValue = state.navigation.localizationMaxPredictFrames,
                onRightChange = { value: String -> viewModel.updateNavigation { copy(localizationMaxPredictFrames = value) } }
            )
            Spacer(Modifier.height(6.dp))
            LabeledCheckbox("剔除偏差过大定位", state.navigation.localizationOutlierRejectionEnabled) { enabled: Boolean ->
                viewModel.updateNavigation { copy(localizationOutlierRejectionEnabled = enabled) }
            }
            TwoFieldRow(
                leftLabel = "最大跳跃(px)",
                leftValue = state.navigation.maxLocalizationJumpPx,
                onLeftChange = { value: String -> viewModel.updateNavigation { copy(maxLocalizationJumpPx = value) } },
                rightLabel = "",
                rightValue = "",
                onRightChange = {}
            )
            Text(
                "最大跳跃为 0 时按移动步长×3 自动计算；导航运行时将实时刷新「地图路径」预览。",
                color = mutedText(),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text("屏幕标定", style = MaterialTheme.typography.labelMedium, color = mutedText())
            Text(
                "先分析获得当前地图点，再在「点击预览」上点选地面位置，然后添加标定点。至少 3 组后启用。",
                color = mutedText(),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            LabeledCheckbox("启用屏幕标定", state.navigation.screenCalibrationEnabled) { enabled: Boolean ->
                viewModel.updateNavigation { copy(screenCalibrationEnabled = enabled) }
            }
            TwoFieldRow(
                leftLabel = "屏幕 X",
                leftValue = state.pendingCalibrationScreenX,
                onLeftChange = viewModel::setPendingCalibrationScreenX,
                rightLabel = "屏幕 Y",
                rightValue = state.pendingCalibrationScreenY,
                onRightChange = viewModel::setPendingCalibrationScreenY
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::addCalibrationPointFromCurrentMapLocation) { Text("添加标定点") }
                Button(onClick = viewModel::clearCalibrationPoints) { Text("清空标定点") }
            }
            if (state.navigation.calibrationPoints.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                state.navigation.calibrationPoints.forEachIndexed { index, point ->
                    Text(
                        "#${index + 1} map(${point.mapX}, ${point.mapY}) -> screen(${point.screenX}, ${point.screenY})",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedText()
                    )
                }
            }
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeftRailCaptureTab(viewModel: StudioViewModel) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::captureGameWindow) { Text("捕获窗口") }
                Button(onClick = {
                    chooseImageFile()?.let(viewModel::loadLocalImage)
                }) { Text("加载本地图像") }
                Button(onClick = viewModel::loadSampleImageOnly) { Text("加载示例图") }
            }
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeftRailMapTab(state: StudioUiState, viewModel: StudioViewModel) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "未开游戏时可用本地上传测试：窗口截图按 ROI 裁剪；若已是裁好的地图，用「上传地图原图」。",
                color = mutedText()
            )
            Spacer(Modifier.height(8.dp))
            RegionEditor("地图 ROI", state.mapPreprocess.mapRegion) { updatedRegion: RegionForm ->
                viewModel.updateMapRegion { updatedRegion }
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = viewModel::startMapRegionPick, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.regionPickTarget == RegionPickTarget.MAP) "正在框选地图…" else "在源图上框选地图")
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::captureAndExtractGameMap) { Text("捕获并提取") }
                Button(onClick = viewModel::extractMapFromCurrentCapture) { Text("从当前截图提取") }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    chooseImageFile()?.let(viewModel::loadLocalScreenshotForMap)
                }) { Text("上传窗口截图") }
                Button(onClick = {
                    chooseImageFile()?.let(viewModel::loadLocalMapImage)
                }) { Text("上传地图原图") }
            }
            Spacer(Modifier.height(8.dp))
            LabeledCheckbox("二值化", state.mapPreprocess.binaryEnabled) { enabled: Boolean ->
                viewModel.updateMapPreprocess { copy(binaryEnabled = enabled) }
            }
            LabeledCheckbox("Otsu 自动阈值", state.mapPreprocess.useOtsu) { enabled: Boolean ->
                viewModel.updateMapPreprocess { copy(useOtsu = enabled) }
            }
            if (!state.mapPreprocess.useOtsu) {
                Text("阈值 ${state.mapPreprocess.threshold}", style = MaterialTheme.typography.labelMedium, color = mutedText())
                Slider(
                    value = state.mapPreprocess.threshold.toFloat(),
                    onValueChange = { viewModel.updateMapPreprocess { copy(threshold = it.toInt()) } },
                    valueRange = 0f..255f
                )
            }
            LabeledCheckbox("去除橙色标记(人物箭头等)", state.mapPreprocess.removeOrangeMarkers) { enabled: Boolean ->
                viewModel.updateMapPreprocess { copy(removeOrangeMarkers = enabled) }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::refreshPathfindingMap, modifier = Modifier.fillMaxWidth()) {
                Text("生成寻路地图")
            }
            Spacer(Modifier.height(8.dp))
            LabeledField("导航 mapImage", state.mapImagePath) { value: String ->
                viewModel.updateMapImagePath(value)
            }
            Spacer(Modifier.height(8.dp))
            Text("手动修补", style = MaterialTheme.typography.labelMedium, color = mutedText())
            Text(
                "导出后在 Paint.NET 等工具中用白色补墙、黑色保留可走区，再导入。",
                color = mutedText(),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    chooseSaveImageFile("导出寻路地图", "pathfinding_map.bmp")?.let(viewModel::exportMapForManualEdit)
                }) { Text("导出供编辑") }
                Button(onClick = {
                    chooseImageFile()?.let(viewModel::importEditedPathfindingMap)
                }) { Text("导入已编辑") }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    chooseSaveImageFile("保存地图原图")?.let(viewModel::saveMapOriginalImage)
                }) { Text("保存原图") }
                Button(onClick = {
                    chooseSaveImageFile("保存寻路地图")?.let(viewModel::savePathfindingMapImage)
                }) { Text("保存寻路图") }
                Button(onClick = {
                    chooseSaveImageFile("应用为导航地图")?.let(viewModel::applyPathfindingMapToNavigation)
                }) { Text("应用为导航地图") }
            }
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeftRailClosureTab(state: StudioUiState, viewModel: StudioViewModel) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "单独上传一张寻路二值图进行检测，与上方制作流程解耦。",
                color = mutedText(),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    chooseImageFile()?.let(viewModel::loadClosureTestImage)
                }) { Text("上传检测图") }
                Button(onClick = viewModel::useCurrentPathfindingMapForClosureTest) { Text("使用当前寻路图") }
            }
            if (state.closureTestImageName.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("检测对象: ${state.closureTestImageName}", style = MaterialTheme.typography.bodySmall, color = mutedText())
            }
            Spacer(Modifier.height(8.dp))
            LabeledField("可走阈值(≤为可走)", state.mapClosure.walkableThreshold) { value: String ->
                viewModel.updateMapClosure { copy(walkableThreshold = value) }
            }
            Button(onClick = viewModel::testMapClosure, modifier = Modifier.fillMaxWidth()) {
                Text("检测是否闭合")
            }
            if (state.mapClosureSummary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    state.mapClosureSummary,
                    color = if (state.mapClosureClosed == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "端点计数法：0 个端点=闭合，≥2 个端点=未闭合。检测后在预览「闭合检测」查看断点。",
                color = mutedText(),
                style = MaterialTheme.typography.bodySmall
            )
        }
}

@Composable
private fun PreviewPane(state: StudioUiState, viewModel: StudioViewModel, modifier: Modifier = Modifier) {
    val visibleTabs = remember {
        if (SHOW_OCR_AND_YOLO_UI) {
            PreviewTab.entries
        } else {
            listOf(
                PreviewTab.SOURCE,
                PreviewTab.MAP_ORIGINAL,
                PreviewTab.MAP_PATHFINDING,
                PreviewTab.MAP_CLOSURE_INPUT,
                PreviewTab.MAP_CLOSURE_LEAK,
                PreviewTab.MAP_CLOSURE_SEAL,
                PreviewTab.PROCESSED,
                PreviewTab.MINIMAP,
                PreviewTab.MAP,
                PreviewTab.CLICK
            )
        }
    }
    val activeTab = state.selectedPreviewTab.takeIf(visibleTabs::contains) ?: visibleTabs.first()
    val previewImage = when (activeTab) {
        PreviewTab.SOURCE -> state.sourceImage
        PreviewTab.MAP_ORIGINAL -> state.mapOriginalImage
        PreviewTab.MAP_PATHFINDING -> state.mapPathfindingImage
        PreviewTab.MAP_CLOSURE_INPUT -> state.closureTestImage
        PreviewTab.MAP_CLOSURE_LEAK -> state.mapClosureLeakPreview
        PreviewTab.MAP_CLOSURE_SEAL -> state.mapClosureSealPreview
        PreviewTab.PROCESSED -> state.processedImage
        PreviewTab.MINIMAP -> state.miniMapPreviewImage
        PreviewTab.MAP -> state.mapPreviewImage
        PreviewTab.CLICK -> state.clickPreviewImage
        PreviewTab.OCR -> state.ocrOverlayImage
        PreviewTab.YOLO -> state.yoloOverlayImage
        PreviewTab.COMBINED -> state.combinedPreviewImage
    }
    val displayImage = state.debugArtifactPreview ?: previewImage
    Pane("预览", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(state.sourceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (state.boundsText.isNotBlank()) {
                    Text(state.boundsText, style = MaterialTheme.typography.bodySmall, color = mutedText())
                }
            }
            Text(state.status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        ScrollableTabRow(selectedTabIndex = visibleTabs.indexOf(activeTab)) {
            visibleTabs.forEach { tab: PreviewTab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { viewModel.selectPreviewTab(tab) },
                    text = { Text(tab.label) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (state.debugArtifactPreview != null && state.debugArtifactLabel.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "诊断图: ${state.debugArtifactLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = viewModel::clearNavigationDebugArtifactPreview) { Text("恢复 Tab 预览") }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (activeTab == PreviewTab.MAP_CLOSURE_LEAK && state.mapClosureLeakPreview != null) {
            Text(
                "紫十字=墙体端点（断点），红=泛洪，蓝=内部未到达。端点处用白色补墙。",
                style = MaterialTheme.typography.bodySmall,
                color = mutedText()
            )
            Spacer(Modifier.height(6.dp))
        }
        if (state.regionPickTarget != RegionPickTarget.NONE && state.sourceImage != null) {
            val pickLabel = when (state.regionPickTarget) {
                RegionPickTarget.MINIMAP -> "小地图"
                RegionPickTarget.MAP -> "地图"
                RegionPickTarget.NONE -> ""
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "在源图上拖拽框选${pickLabel}区域，松开鼠标完成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = viewModel::cancelRegionPick) { Text("取消框选") }
            }
            Spacer(Modifier.height(6.dp))
            RegionPickableImage(
                image = state.sourceImage,
                existingRegion = when (state.regionPickTarget) {
                    RegionPickTarget.MINIMAP -> state.miniMapRegion
                    RegionPickTarget.MAP -> state.mapPreprocess.mapRegion
                    RegionPickTarget.NONE -> null
                },
                onRegionSelected = viewModel::applyPickedRegion,
                modifier = Modifier.fillMaxSize()
            )
        } else {
        ZoomableImage(
            image = displayImage,
            label = activeTab.label,
            modifier = Modifier.fillMaxSize(),
            onImageClick = if (activeTab == PreviewTab.CLICK) {
                { x: Int, y: Int -> viewModel.setPendingCalibrationScreenPoint(x, y) }
            } else {
                null
            }
        )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RightRail(state: StudioUiState, viewModel: StudioViewModel, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Pane("寻路链路诊断", modifier = Modifier.fillMaxWidth()) {
            Text(
                "逐步执行寻路各环节，定位失败步骤。已有源图时步骤 1 可跳过，直接从步骤 2 开始。",
                color = mutedText(),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::runAllNavigationDebugSteps) { Text("逐步运行全部") }
                Button(onClick = viewModel::resetNavigationDebug) { Text("重置") }
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.navigationDebugSteps.forEach { item ->
                    NavigationDebugStepRow(
                        item = item,
                        running = state.navigationDebugRunningStep == item.step,
                        onRun = { viewModel.runNavigationDebugStep(item.step) },
                        onArtifactClick = { artifact -> viewModel.showNavigationDebugArtifact(artifact.label, artifact.image) }
                    )
                }
            }
        }

        Pane("分析与执行", modifier = Modifier.fillMaxWidth()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::runNavigationPreflight) {
                    Text(if (state.preflightRunning) "预检中…" else "一键预检")
                }
                Button(onClick = { viewModel.analyzeCurrentImage(false) }) { Text("分析") }
                Button(onClick = { viewModel.analyzeCurrentImage(true) }) { Text("点击预览") }
                Button(onClick = viewModel::executeSingleStep) { Text("执行单步") }
                Button(onClick = viewModel::startContinuousNavigation) { Text("开始连续导航") }
                Button(onClick = viewModel::stopNavigation) { Text("停止导航") }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            SummaryLine("当前地图点", state.currentPointText)
            SummaryLine("目标点", state.targetPointText)
            SummaryLine("下一次点击", state.nextClickText)
            SummaryLine("导航状态", if (state.navigationRunning) "运行中" else "空闲")
            SummaryLine("控制器状态", state.navigationRuntimeText)
            SummaryLine("路点索引", state.navigationWaypointIndex.toString())
            SummaryLine("定位置信度", state.navigationConfidenceText)
            if (state.localizationDetailText.isNotBlank()) {
                SummaryLine("定位修正", state.localizationDetailText)
            }
        }

        Pane("闭合检测", modifier = Modifier.fillMaxWidth()) {
            Text(
                "密封预览参数。检测图在左侧「闭合检测」面板上传。",
                color = mutedText(),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            LabeledCheckbox("外部泛洪", state.mapClosure.sealExterior) { enabled: Boolean ->
                viewModel.updateMapClosure { copy(sealExterior = enabled) }
            }
            TwoFieldRow(
                leftLabel = "边缘密封(px)",
                leftValue = state.mapClosure.sealBorderWidth,
                onLeftChange = { value: String -> viewModel.updateMapClosure { copy(sealBorderWidth = value) } },
                rightLabel = "墙缝闭合核",
                rightValue = state.mapClosure.morphCloseKernelSize,
                onRightChange = { value: String -> viewModel.updateMapClosure { copy(morphCloseKernelSize = value) } }
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::previewMapClosureSeal) { Text("预览密封效果") }
            }
        }

        if (SHOW_OCR_AND_YOLO_UI) {
            Pane("OCR 结果", modifier = Modifier.fillMaxWidth().weight(0.23f)) {
                if (state.ocrResults.isEmpty()) {
                    EmptyHint("还没有 OCR 输出。")
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.ocrResults.forEach { result -> OcrCard(result) }
                    }
                }
            }

            Pane("YOLO 检测", modifier = Modifier.fillMaxWidth().weight(0.20f)) {
                if (state.detections.isEmpty()) {
                    EmptyHint("还没有检测框。")
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.detections.forEach { detection ->
                            SummaryLine(
                                detection.label(),
                                "${"%.2f".format(detection.score())}  ${detection.bounds().x},${detection.bounds().y} ${detection.bounds().width}x${detection.bounds().height}"
                            )
                        }
                    }
                }
            }
        }

        Pane("UI Automation", modifier = Modifier.fillMaxWidth()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::inspectUiAutomationWindow) { Text("检查窗口") }
                Button(onClick = viewModel::runUiAutomationRules) { Text("运行规则") }
            }
            Spacer(Modifier.height(8.dp))
            LabeledCheckbox("启用 UI Automation", state.uiAutomation.enabled) { enabled: Boolean ->
                viewModel.updateUiAutomation { copy(enabled = enabled) }
            }
            Spacer(Modifier.height(8.dp))
            LabeledField("Helper Command", state.uiAutomation.helperCommand) { value: String ->
                viewModel.updateUiAutomation { copy(helperCommand = value) }
            }
            LabeledField("Timeout Ms", state.uiAutomation.timeoutMs) { value: String ->
                viewModel.updateUiAutomation { copy(timeoutMs = value) }
            }
            Spacer(Modifier.height(8.dp))
            Text("规则", style = MaterialTheme.typography.labelMedium, color = mutedText())
            if (state.uiAutomation.rules.isEmpty()) {
                EmptyHint("没有配置规则。")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.uiAutomation.rules.forEach { rule ->
                        SummaryLine(rule.name, "${rule.windowTitleContains} / ${rule.actions.size} actions")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("已检查元素", style = MaterialTheme.typography.labelMedium, color = mutedText())
            if (state.inspectedUiElements.isEmpty()) {
                EmptyHint("还没有检查结果。")
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.inspectedUiElements.take(12).forEach { element ->
                        SummaryLine(element.controlType(), listOf(element.name(), element.automationId()).filter { it.isNotBlank() }.joinToString(" / "))
                    }
                }
            }
            if (state.uiAutomationExecutions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("规则执行", style = MaterialTheme.typography.labelMedium, color = mutedText())
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.uiAutomationExecutions.forEach { execution ->
                        SummaryLine(execution.ruleName(), if (execution.success()) "成功" else execution.message())
                    }
                }
            }
        }

        Pane("日志", modifier = Modifier.fillMaxWidth().height(220.dp)) {
            if (state.logs.isEmpty()) {
                EmptyHint("日志会在这里滚动。")
            } else {
                SelectionContainer {
                    Text(
                        state.logs.joinToString("\n"),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NavigationDebugStepRow(
    item: NavigationDebugStepStatus,
    running: Boolean,
    onRun: () -> Unit,
    onArtifactClick: (NavigationDebugArtifactUi) -> Unit
) {
    val statusText = when (item.success) {
        true -> "成功"
        false -> "失败"
        null -> if (running) "运行中…" else "未运行"
    }
    val statusColor = when (item.success) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> mutedText()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${item.step.order()}. ${item.step.label()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onRun, enabled = !running) {
                Text(if (running) "…" else "运行")
            }
        }
        Text(item.step.hint(), color = mutedText(), style = MaterialTheme.typography.bodySmall)
        Text(statusText, color = statusColor, style = MaterialTheme.typography.labelMedium)
        if (item.message.isNotBlank()) {
            Text(item.message, style = MaterialTheme.typography.bodySmall)
        }
        if (item.artifactDir.isNotBlank()) {
            Text(
                "图片目录: ${item.artifactDir}",
                style = MaterialTheme.typography.bodySmall,
                color = mutedText()
            )
        }
        if (item.artifacts.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.artifacts.forEach { artifact ->
                    FilterChip(
                        selected = false,
                        onClick = { onArtifactClick(artifact) },
                        label = { Text(artifact.label, style = MaterialTheme.typography.labelSmall) },
                        enabled = artifact.image != null
                    )
                }
            }
        }
    }
}

private data class FitImageLayout(
    val displayWidth: Float,
    val displayHeight: Float,
    val offsetX: Float,
    val offsetY: Float,
    val imageWidth: Int,
    val imageHeight: Int
) {
    fun pointerToImage(pointer: Offset): Offset? {
        val localX = pointer.x - offsetX
        val localY = pointer.y - offsetY
        if (localX < 0f || localY < 0f || localX > displayWidth || localY > displayHeight) {
            return null
        }
        return Offset(
            localX / displayWidth * imageWidth,
            localY / displayHeight * imageHeight
        )
    }

    fun imageRectToDisplay(region: RegionForm): Rect? {
        val x = region.x.toFloatOrNull() ?: return null
        val y = region.y.toFloatOrNull() ?: return null
        val width = region.width.toFloatOrNull() ?: return null
        val height = region.height.toFloatOrNull() ?: return null
        if (width <= 0f || height <= 0f) {
            return null
        }
        return Rect(
            offsetX + x / imageWidth * displayWidth,
            offsetY + y / imageHeight * displayHeight,
            offsetX + (x + width) / imageWidth * displayWidth,
            offsetY + (y + height) / imageHeight * displayHeight
        )
    }
}

private fun fitImageLayout(containerWidth: Float, containerHeight: Float, imageWidth: Int, imageHeight: Int): FitImageLayout {
    if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) {
        return FitImageLayout(containerWidth, containerHeight, 0f, 0f, imageWidth, imageHeight)
    }
    val imageAspect = imageWidth.toFloat() / imageHeight
    val containerAspect = containerWidth / containerHeight
    return if (imageAspect > containerAspect) {
        val displayWidth = containerWidth
        val displayHeight = containerWidth / imageAspect
        FitImageLayout(
            displayWidth,
            displayHeight,
            0f,
            (containerHeight - displayHeight) / 2f,
            imageWidth,
            imageHeight
        )
    } else {
        val displayHeight = containerHeight
        val displayWidth = containerHeight * imageAspect
        FitImageLayout(
            displayWidth,
            displayHeight,
            (containerWidth - displayWidth) / 2f,
            0f,
            imageWidth,
            imageHeight
        )
    }
}

@Composable
private fun RegionPickableImage(
    image: BufferedImage,
    existingRegion: RegionForm?,
    onRegionSelected: (Int, Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(image) { image.toComposeImageBitmap() }
    var dragStart by remember(image) { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember(image) { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .background(Color(0xFF111619), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = fitImageLayout(
                constraints.maxWidth.toFloat(),
                constraints.maxHeight.toFloat(),
                image.width,
                image.height
            )
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = "区域框选",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(image) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (layout.pointerToImage(offset) != null) {
                                    dragStart = offset
                                    dragCurrent = offset
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragCurrent = change.position
                            },
                            onDragEnd = {
                                val start = dragStart?.let(layout::pointerToImage)
                                val end = dragCurrent?.let(layout::pointerToImage)
                                dragStart = null
                                dragCurrent = null
                                if (start != null && end != null) {
                                    val left = min(start.x, end.x).roundToInt().coerceIn(0, image.width - 1)
                                    val top = min(start.y, end.y).roundToInt().coerceIn(0, image.height - 1)
                                    val right = max(start.x, end.x).roundToInt().coerceIn(left + 1, image.width)
                                    val bottom = max(start.y, end.y).roundToInt().coerceIn(top + 1, image.height)
                                    onRegionSelected(left, top, right - left, bottom - top)
                                }
                            },
                            onDragCancel = {
                                dragStart = null
                                dragCurrent = null
                            }
                        )
                    }
            ) {
                existingRegion?.let { region ->
                    layout.imageRectToDisplay(region)?.let { rect ->
                        drawRect(
                            color = Color(0x663CC6A8),
                            topLeft = rect.topLeft,
                            size = Size(rect.width, rect.height),
                            style = Stroke(width = 2f)
                        )
                    }
                }
                val start = dragStart
                val current = dragCurrent
                if (start != null && current != null) {
                    drawRect(
                        color = Color(0xCCF2C14E),
                        topLeft = Offset(min(start.x, current.x), min(start.y, current.y)),
                        size = Size(abs(current.x - start.x), abs(current.y - start.y)),
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    image: BufferedImage?,
    label: String,
    modifier: Modifier = Modifier,
    onImageClick: ((Int, Int) -> Unit)? = null
) {
    var zoom by remember(image) { mutableStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    val bitmap = remember(image) { image?.toComposeImageBitmap() }
    Box(
        modifier = modifier
            .background(Color(0xFF111619), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
    ) {
        if (bitmap == null) {
            EmptyHint("当前标签页还没有图像。", Modifier.align(Alignment.Center))
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset += Offset(dragAmount.x, dragAmount.y)
                        }
                    }
            ) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .then(
                            if (onImageClick != null) {
                                Modifier.pointerInput(bitmap) {
                                    detectTapGestures { tapOffset ->
                                        onImageClick(tapOffset.x.toInt(), tapOffset.y.toInt())
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TinyButton("-") { zoom = (zoom - 0.1f).coerceAtLeast(0.5f) }
                TinyButton("Reset") {
                    zoom = 1f
                    offset = Offset.Zero
                }
                TinyButton("+") { zoom = (zoom + 0.1f).coerceAtMost(4f) }
            }
        }
    }
}

@Composable
private fun TinyButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun OcrCard(result: OcrResult) {
    val previewBitmap = remember(result.preprocessedPreviewImage()) { result.preprocessedPreviewImage()?.toComposeImageBitmap() }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.name(), fontWeight = FontWeight.SemiBold)
            Text(result.text().ifBlank { "(空)" }, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "conf ${"%.1f".format(result.confidence())} / ${result.bounds().x},${result.bounds().y} ${result.bounds().width}x${result.bounds().height}",
                style = MaterialTheme.typography.bodySmall,
                color = mutedText()
            )
            if (previewBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = previewBitmap,
                    contentDescription = "${result.name()} OCR preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(Color(0xFF111619), RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun Pane(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun InlineSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = mutedText())
            content()
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onValueChange: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = mutedText())
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun LabeledCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun TwoFieldRow(
    leftLabel: String,
    leftValue: String,
    onLeftChange: (String) -> Unit,
    rightLabel: String,
    rightValue: String,
    onRightChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) { LabeledField(leftLabel, leftValue, onLeftChange) }
        Column(Modifier.weight(1f)) { LabeledField(rightLabel, rightValue, onRightChange) }
    }
}

@Composable
private fun RegionEditor(title: String, region: RegionForm, onChange: (RegionForm) -> Unit) {
    if (title.isNotBlank()) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = mutedText())
        Spacer(Modifier.height(6.dp))
    }
    TwoFieldRow("X", region.x, { value -> onChange(region.copy(x = value)) }, "Y", region.y, { value -> onChange(region.copy(y = value)) })
    Spacer(Modifier.height(6.dp))
    TwoFieldRow("宽", region.width, { value -> onChange(region.copy(width = value)) }, "高", region.height, { value -> onChange(region.copy(height = value)) })
}

@Composable
private fun PointEditor(title: String, point: PointForm, onChange: (PointForm) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = mutedText())
    Spacer(Modifier.height(6.dp))
    TwoFieldRow("X", point.x, { value -> onChange(point.copy(x = value)) }, "Y", point.y, { value -> onChange(point.copy(y = value)) })
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = mutedText(), style = MaterialTheme.typography.bodySmall)
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = mutedText(), style = MaterialTheme.typography.bodySmall)
}

private fun chooseImageFile(): java.io.File? {
    val dialog = FileDialog(null as Frame?, "选择图像")
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return java.io.File(directory, file)
}

private fun chooseSaveImageFile(title: String, defaultFileName: String = "largeMap.bmp"): java.io.File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
    dialog.file = defaultFileName
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return java.io.File(directory, file)
}

@Composable
private fun mutedText(): Color = MaterialTheme.colorScheme.onSurfaceVariant

private fun studioColors() = darkColorScheme(
    background = Color(0xFF0F1316),
    surface = Color(0xFF171D21),
    surfaceVariant = Color(0xFF202930),
    primary = Color(0xFF3CC6A8),
    secondary = Color(0xFFE07A5F),
    tertiary = Color(0xFFF2C14E),
    onPrimary = Color(0xFF04120E),
    onSecondary = Color(0xFF1E0E09),
    onTertiary = Color(0xFF1C1402),
    onBackground = Color(0xFFE7ECEF),
    onSurface = Color(0xFFE7ECEF),
    onSurfaceVariant = Color(0xFFB6C2C9),
    outlineVariant = Color(0xFF34424B)
)
