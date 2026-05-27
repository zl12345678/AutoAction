package com.auto.vision;

import com.auto.config.ClickBackend;
import com.auto.config.VisionConfig;
import com.auto.input.InputController;
import com.auto.opencv.process.ArrowMatchResult;
import com.auto.window.WindowCaptureFrames;
import com.auto.window.WindowRef;
import com.auto.window.WindowService;
import org.opencv.core.Point;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NavigationPipelineDebugger {
    private final WindowService windowService;
    private final OpenCvNavigationAnalyzer analyzer;
    private final InputController inputController;
    private NavigationDebugArtifactWriter artifactWriter = NavigationDebugArtifactWriter.openNewSession();

    private BufferedImage sourceImage;
    private Rectangle sourceBounds;
    private Rectangle clientBoundsOnScreen;
    private String sourceName = "";
    private BufferedImage miniMapImage;
    private Point arrowCenter;
    private double arrowConfidence;
    private Point currentMapPoint;
    private double localizationConfidence;
    private int[][] path = new int[0][2];
    private Point nextMapPoint;
    private Point nextScreenPoint;
    private NavigationAnalysis lastAnalysis;
    private NavigationDecision lastDecision;
    private volatile ClickBackend clickBackend = ClickBackend.WIN32;

    public NavigationPipelineDebugger(
            WindowService windowService,
            OpenCvNavigationAnalyzer analyzer,
            InputController inputController
    ) {
        this.windowService = windowService;
        this.analyzer = analyzer;
        this.inputController = inputController;
    }

    public void setClickBackend(ClickBackend clickBackend) {
        this.clickBackend = clickBackend == null ? ClickBackend.WIN32 : clickBackend;
    }

    public void reset() {
        sourceImage = null;
        sourceBounds = null;
        clientBoundsOnScreen = null;
        sourceName = "";
        miniMapImage = null;
        arrowCenter = null;
        arrowConfidence = 0.0;
        currentMapPoint = null;
        localizationConfidence = 0.0;
        path = new int[0][2];
        nextMapPoint = null;
        nextScreenPoint = null;
        lastAnalysis = null;
        lastDecision = null;
        analyzer.resetLocalizationState();
        artifactWriter = NavigationDebugArtifactWriter.openNewSession();
    }

    private NavigationDebugStepResult finish(
            NavigationDebugStep step,
            boolean success,
            String message,
            PreviewTab tab,
            NavigationAnalysis analysis,
            List<NavigationDebugArtifact> artifacts
    ) {
        List<NavigationDebugArtifact> saved = artifacts == null ? List.of() : artifacts;
        Path dir = artifactWriter.writeStep(step, saved);
        String dirText = dir == null ? artifactWriter.sessionDir().toString() : dir.toString();
        return new NavigationDebugStepResult(step, success, message, tab, analysis, saved, dirText);
    }

    private NavigationAnalysis withSource(NavigationAnalysis analysis) {
        if (analysis == null || sourceImage == null) {
            return analysis;
        }
        if (analysis.sourceImage() != null) {
            return analysis;
        }
        return new NavigationAnalysis(
                sourceName,
                sourceImage,
                analysis.miniMapImage(),
                analysis.miniMapPreviewImage(),
                analysis.mapPreviewImage(),
                analysis.clickPreviewImage(),
                analysis.arrowCenter(),
                analysis.currentMapPoint(),
                analysis.targetMapPoint(),
                analysis.nextMapPoint(),
                analysis.nextScreenPoint(),
                analysis.path(),
                analysis.arrived(),
                analysis.success(),
                analysis.message(),
                analysis.localizationConfidence(),
                analysis.localizationMethod(),
                analysis.localizationRawPoint(),
                analysis.localizationOutlierRejected(),
                analysis.localizationDetail()
        );
    }

    public NavigationDebugStepResult runStep(NavigationDebugStep step, VisionConfig config, boolean dryRun) {
        return switch (step) {
            case CAPTURE_WINDOW -> captureWindow(config);
            case CROP_MINIMAP -> cropMiniMap(config);
            case MATCH_ARROW -> matchArrow(config);
            case LOCATE_ON_MAP -> locateOnMap(config);
            case PLAN_PATH -> planPath(config);
            case MAP_TO_SCREEN -> mapToScreen(config);
            case NAV_DECISION -> navDecision(config);
            case EXECUTE_CLICK -> executeClick(config, dryRun);
        };
    }

    private NavigationDebugStepResult captureWindow(VisionConfig config) {
        List<NavigationDebugArtifact> artifacts = new ArrayList<>();
        if (sourceImage != null && sourceBounds != null) {
            artifacts.add(NavigationDebugArtifacts.image("source", "源图", sourceImage));
            return finish(
                    NavigationDebugStep.CAPTURE_WINDOW,
                    true,
                    "复用已加载源图 " + sourceName + " " + rectText(sourceBounds),
                    PreviewTab.SOURCE,
                    null,
                    artifacts
            );
        }
        var window = windowService.findWindow(config.windowTitle());
        if (window.isEmpty()) {
            return finish(
                    NavigationDebugStep.CAPTURE_WINDOW,
                    false,
                    "未找到窗口: " + config.windowTitle(),
                    null,
                    null,
                    artifacts
            );
        }
        WindowRef windowRef = window.get();
        WindowCaptureFrames frames = WindowCaptureFrames.capture(windowService, windowRef);
        sourceImage = frames.image();
        sourceBounds = new Rectangle(frames.windowBoundsOnScreen());
        clientBoundsOnScreen = new Rectangle(frames.clientBoundsOnScreen());
        sourceName = "window:" + windowRef.title();
        miniMapImage = null;
        arrowCenter = null;
        currentMapPoint = null;
        path = new int[0][2];
        nextMapPoint = null;
        nextScreenPoint = null;
        lastAnalysis = null;
        lastAnalysis = null;
        artifacts.add(NavigationDebugArtifacts.image("source", "源图", sourceImage));
        return finish(
                NavigationDebugStep.CAPTURE_WINDOW,
                true,
                "已捕获窗口 " + windowRef.title() + " " + rectText(sourceBounds),
                PreviewTab.SOURCE,
                null,
                artifacts
        );
    }

    private NavigationDebugStepResult cropMiniMap(VisionConfig config) {
        BufferedImage source = requireSource(NavigationDebugStep.CROP_MINIMAP);
        if (source == null) {
            return finish(NavigationDebugStep.CROP_MINIMAP, false, "请先执行步骤 1 或加载源图。", null, null, List.of());
        }
        List<NavigationDebugArtifact> artifacts = new ArrayList<>();
        var region = config.miniMapRegion();
        artifacts.add(NavigationDebugArtifacts.image("source_roi", "源图+小地图ROI", drawRegionOnSource(source, region)));
        try {
            OpenCvNavigationAnalyzer.ArrowCenteredMiniMapCrop crop = analyzer.extractArrowCenteredMiniMap(config, source);
            miniMapImage = crop.miniMapImage();
            arrowCenter = crop.arrowCenter();
            arrowConfidence = crop.arrowConfidence();
            artifacts.add(NavigationDebugArtifacts.image(
                    "source_crop",
                    "源图+箭头中心裁剪框",
                    drawCropOnSource(source, region, crop.sourceCropBounds())
            ));
        } catch (RuntimeException e) {
            return finish(NavigationDebugStep.CROP_MINIMAP, false, e.getMessage(), null, null, artifacts);
        }
        artifacts.add(NavigationDebugArtifacts.image("minimap_crop", "裁剪小地图", miniMapImage));
        return finish(
                NavigationDebugStep.CROP_MINIMAP,
                true,
                "已以箭头为中心裁剪小地图 "
                        + miniMapImage.getWidth() + "x" + miniMapImage.getHeight()
                        + "，箭头 (" + Math.round(arrowCenter.x) + ", " + Math.round(arrowCenter.y) + ")",
                PreviewTab.MINIMAP,
                withSource(analyzer.buildMiniMapPreviewAnalysis(
                        config,
                        source,
                        sourceBounds,
                        sourceName,
                        miniMapImage,
                        arrowCenter,
                        arrowConfidence
                )),
                artifacts
        );
    }

    private NavigationDebugStepResult matchArrow(VisionConfig config) {
        if (miniMapImage == null) {
            return finish(NavigationDebugStep.MATCH_ARROW, false, "请先执行步骤 2 裁剪小地图。", null, null, List.of());
        }
        OpenCvNavigationAnalyzer.ArrowDetectResult detect = analyzer.detectArrowWithDebug(config, miniMapImage);
        ArrowMatchResult match = detect.result();
        arrowCenter = match.center();
        arrowConfidence = match.confidence();
        if (!match.found()) {
            return finish(
                    NavigationDebugStep.MATCH_ARROW,
                    false,
                    "未识别到箭头，请检查小地图 ROI 或橙色 HSV 参数。",
                    PreviewTab.MINIMAP,
                    null,
                    detect.artifacts()
            );
        }
        NavigationAnalysis analysis = withSource(analyzer.buildMiniMapPreviewAnalysis(
                config,
                sourceImage,
                sourceBounds,
                sourceName,
                miniMapImage,
                arrowCenter,
                arrowConfidence
        ));
        return finish(
                NavigationDebugStep.MATCH_ARROW,
                true,
                "箭头中心 (" + Math.round(arrowCenter.x) + ", " + Math.round(arrowCenter.y)
                        + ")，置信度 " + String.format("%.2f", arrowConfidence),
                PreviewTab.MINIMAP,
                analysis,
                detect.artifacts()
        );
    }

    private NavigationDebugStepResult locateOnMap(VisionConfig config) {
        if (miniMapImage == null || arrowCenter == null) {
            return finish(NavigationDebugStep.LOCATE_ON_MAP, false, "请先执行步骤 3 识别箭头。", null, null, List.of());
        }
        analyzer.resetLocalizationState();
        OpenCvNavigationAnalyzer.LocateOnMapDebug debug = analyzer.locateOnMapWithDebug(config, miniMapImage, arrowCenter);
        OpenCvNavigationAnalyzer.LocateResult locate = debug.toLocateResult();
        if (!locate.success()) {
            return finish(
                    NavigationDebugStep.LOCATE_ON_MAP,
                    false,
                    locate.message(),
                    PreviewTab.MAP,
                    locate.analysis(),
                    debug.artifacts()
            );
        }
        currentMapPoint = locate.currentMapPoint();
        localizationConfidence = locate.confidence();
        lastAnalysis = withSource(locate.analysis());
        return finish(
                NavigationDebugStep.LOCATE_ON_MAP,
                true,
                "当前地图坐标 (" + Math.round(currentMapPoint.x) + ", " + Math.round(currentMapPoint.y)
                        + ")，置信度 " + String.format("%.2f", localizationConfidence),
                PreviewTab.MAP,
                lastAnalysis,
                debug.artifacts()
        );
    }

    private NavigationDebugStepResult planPath(VisionConfig config) {
        if (currentMapPoint == null) {
            return finish(NavigationDebugStep.PLAN_PATH, false, "请先执行步骤 4 大地图定位。", null, null, List.of());
        }
        OpenCvNavigationAnalyzer.PathPlanResult plan = analyzer.planPath(config, currentMapPoint, 0);
        if (!plan.success()) {
            return finish(NavigationDebugStep.PLAN_PATH, false, plan.message(), PreviewTab.MAP, plan.analysis(), List.of());
        }
        path = plan.path();
        nextMapPoint = plan.nextMapPoint();
        lastAnalysis = withSource(plan.analysis());
        List<NavigationDebugArtifact> artifacts = new ArrayList<>();
        if (lastAnalysis != null && lastAnalysis.mapPreviewImage() != null) {
            artifacts.add(NavigationDebugArtifacts.image("map_path", "路径规划预览", lastAnalysis.mapPreviewImage()));
        }
        return finish(
                NavigationDebugStep.PLAN_PATH,
                true,
                "路径点数 " + path.length + "，下一路点 (" + Math.round(nextMapPoint.x) + ", "
                        + Math.round(nextMapPoint.y) + ")",
                PreviewTab.MAP,
                lastAnalysis,
                artifacts
        );
    }

    private NavigationDebugStepResult mapToScreen(VisionConfig config) {
        if (lastAnalysis == null || nextMapPoint == null || currentMapPoint == null) {
            return finish(NavigationDebugStep.MAP_TO_SCREEN, false, "请先执行步骤 5 路径规划。", null, null, List.of());
        }
        if (sourceBounds == null) {
            return finish(NavigationDebugStep.MAP_TO_SCREEN, false, "缺少窗口边界，无法映射屏幕坐标。", null, null, List.of());
        }
        Rectangle clientBounds = clientBoundsOnScreen == null ? sourceBounds : clientBoundsOnScreen;
        OpenCvNavigationAnalyzer.ScreenMapResult screen = analyzer.resolveScreenMapping(
                config,
                sourceBounds,
                clientBounds,
                currentMapPoint,
                nextMapPoint,
                lastAnalysis
        );
        nextScreenPoint = screen.screenPoint();
        lastAnalysis = withSource(screen.analysis());
        List<NavigationDebugArtifact> artifacts = new ArrayList<>();
        if (lastAnalysis != null && lastAnalysis.clickPreviewImage() != null) {
            artifacts.add(NavigationDebugArtifacts.image("click_preview", "点击预览", lastAnalysis.clickPreviewImage()));
        }
        if (nextScreenPoint == null) {
            return finish(NavigationDebugStep.MAP_TO_SCREEN, false, "未能计算屏幕点击坐标。", PreviewTab.CLICK, lastAnalysis, artifacts);
        }
        return finish(
                NavigationDebugStep.MAP_TO_SCREEN,
                true,
                "预测屏幕点击 (" + Math.round(nextScreenPoint.x) + ", " + Math.round(nextScreenPoint.y) + ")"
                        + (screen.usedCalibration() ? "，已用屏幕标定" : "，使用 fallback 映射"),
                PreviewTab.CLICK,
                lastAnalysis,
                artifacts
        );
    }

    private NavigationDebugStepResult navDecision(VisionConfig config) {
        if (lastAnalysis == null) {
            return finish(NavigationDebugStep.NAV_DECISION, false, "请先执行步骤 6 屏幕映射。", null, null, List.of());
        }
        NavigationController controller = new NavigationController(config.navigation());
        lastDecision = controller.decide(lastAnalysis, System.currentTimeMillis());
        boolean success = lastDecision.action() != NavigationAction.STOP_FAILED;
        List<NavigationDebugArtifact> artifacts = new ArrayList<>();
        if (lastAnalysis.clickPreviewImage() != null) {
            artifacts.add(NavigationDebugArtifacts.image("decision_preview", "决策预览", lastAnalysis.clickPreviewImage()));
        }
        return finish(
                NavigationDebugStep.NAV_DECISION,
                success,
                lastDecision.state() + " / " + lastDecision.action() + " — " + lastDecision.message(),
                PreviewTab.CLICK,
                lastAnalysis,
                artifacts
        );
    }

    private NavigationDebugStepResult executeClick(VisionConfig config, boolean dryRun) {
        if (lastDecision == null) {
            if (lastAnalysis == null) {
                return finish(NavigationDebugStep.EXECUTE_CLICK, false, "请先执行步骤 7 导航决策。", null, null, List.of());
            }
            NavigationController controller = new NavigationController(config.navigation());
            lastDecision = controller.decide(lastAnalysis, System.currentTimeMillis());
        }
        List<NavigationDebugArtifact> artifacts = new ArrayList<>();
        if (lastAnalysis != null && lastAnalysis.clickPreviewImage() != null) {
            artifacts.add(NavigationDebugArtifacts.image("click_target", "点击目标", lastAnalysis.clickPreviewImage()));
        }
        if (lastDecision.action() == NavigationAction.STOP_ARRIVED) {
            return finish(NavigationDebugStep.EXECUTE_CLICK, true, "已到达目标，无需点击。", PreviewTab.CLICK, lastAnalysis, artifacts);
        }
        if (lastDecision.action() == NavigationAction.STOP_FAILED) {
            return finish(
                    NavigationDebugStep.EXECUTE_CLICK,
                    false,
                    "导航决策失败: " + lastDecision.message(),
                    PreviewTab.CLICK,
                    lastAnalysis,
                    artifacts
            );
        }
        boolean forcedClickDespiteSkip = false;
        Point screenPoint;
        if (lastDecision.action() == NavigationAction.SKIP_CLICK) {
            screenPoint = lastDecision.clickScreenPoint();
            if (screenPoint == null && lastAnalysis != null) {
                screenPoint = lastAnalysis.nextScreenPoint();
            }
            if (screenPoint == null) {
                screenPoint = nextScreenPoint;
            }
            if (screenPoint == null) {
                return finish(
                        NavigationDebugStep.EXECUTE_CLICK,
                        true,
                        "当前决策为跳过点击: " + lastDecision.message(),
                        PreviewTab.CLICK,
                        lastAnalysis,
                        artifacts
                );
            }
            forcedClickDespiteSkip = true;
        } else {
            screenPoint = lastDecision.clickScreenPoint();
            if (screenPoint == null) {
                screenPoint = nextScreenPoint;
            }
            if (screenPoint == null && lastAnalysis != null) {
                screenPoint = lastAnalysis.nextScreenPoint();
            }
        }
        if (screenPoint == null) {
            return finish(NavigationDebugStep.EXECUTE_CLICK, false, "缺少屏幕点击坐标。", PreviewTab.CLICK, lastAnalysis, artifacts);
        }
        if (dryRun) {
            return finish(
                    NavigationDebugStep.EXECUTE_CLICK,
                    false,
                    "Dry Run 已开启：未移动鼠标、未点击、未聚焦游戏。"
                            + "请取消勾选「Dry Run」后重试。"
                            + "计划点击 (" + Math.round(screenPoint.x) + ", " + Math.round(screenPoint.y) + ")",
                    PreviewTab.CLICK,
                    lastAnalysis,
                    artifacts
            );
        }
        var window = windowService.findWindow(config.windowTitle());
        if (window.isEmpty()) {
            return finish(
                    NavigationDebugStep.EXECUTE_CLICK,
                    false,
                    "未找到窗口「" + config.windowTitle() + "」，无法聚焦或点击。",
                    PreviewTab.CLICK,
                    lastAnalysis,
                    artifacts
            );
        }
        com.auto.window.GameWindowClickResult clickResult = com.auto.window.GameWindowClickHelper.click(
                windowService,
                inputController,
                window.get(),
                new java.awt.Point((int) Math.round(screenPoint.x), (int) Math.round(screenPoint.y)),
                clickBackend
        );
        System.out.println("Navigation debug click: " + clickResult.summary());
        if (!clickResult.focusSucceeded()) {
            return finish(
                    NavigationDebugStep.EXECUTE_CLICK,
                    false,
                    "已发送 " + clickResult.clickCount() + " 次点击，但游戏窗口仍未聚焦。"
                            + "请确认窗口标题、游戏是否最小化，或尝试以管理员身份运行本程序。"
                            + "\n" + clickResult.summary(),
                    PreviewTab.CLICK,
                    lastAnalysis,
                    artifacts
            );
        }
        String message = clickResult.summary();
        if (forcedClickDespiteSkip) {
            message = "诊断强制点击（决策为跳过）。" + message;
        }
        return finish(
                NavigationDebugStep.EXECUTE_CLICK,
                true,
                message,
                PreviewTab.CLICK,
                lastAnalysis,
                artifacts
        );
    }

    private static BufferedImage drawRegionOnSource(BufferedImage source, com.auto.config.RegionConfig region) {
        BufferedImage copy = NavigationDebugArtifactWriter.copyImage(source);
        Graphics2D graphics = copy.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.drawRect(region.x(), region.y(), region.width(), region.height());
        graphics.dispose();
        return copy;
    }

    private static BufferedImage drawCropOnSource(
            BufferedImage source,
            com.auto.config.RegionConfig searchRegion,
            Rectangle cropBounds
    ) {
        BufferedImage copy = drawRegionOnSource(source, searchRegion);
        Graphics2D graphics = copy.createGraphics();
        graphics.setColor(Color.CYAN);
        graphics.drawRect(cropBounds.x, cropBounds.y, cropBounds.width, cropBounds.height);
        graphics.dispose();
        return copy;
    }

    public void adoptExistingCapture(BufferedImage image, Rectangle bounds, String name) {
        sourceImage = image;
        sourceBounds = bounds == null ? new Rectangle(0, 0, image.getWidth(), image.getHeight()) : new Rectangle(bounds);
        clientBoundsOnScreen = sourceBounds;
        sourceName = name == null ? "" : name;
    }

    public BufferedImage getSourceImage() {
        return sourceImage;
    }

    public Rectangle getSourceBounds() {
        return sourceBounds;
    }

    public String getSourceName() {
        return sourceName;
    }

    private BufferedImage requireSource(NavigationDebugStep step) {
        return sourceImage;
    }

    private static String rectText(Rectangle rect) {
        return "(" + rect.x + ", " + rect.y + ", " + rect.width + ", " + rect.height + ")";
    }
}
