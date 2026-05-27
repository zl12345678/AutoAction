package com.auto.vision;

import com.auto.config.ClickBackend;
import com.auto.config.RegionConfig;
import com.auto.config.ScreenCalibrationConfig;
import com.auto.config.VisionConfig;
import com.auto.input.interception.InterceptionBootstrap;
import com.auto.input.interception.InterceptionMouseInput;
import com.auto.opencv.utils.ImageProcessor;
import com.auto.window.WindowRef;
import com.auto.window.WindowService;
import org.opencv.core.Mat;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One-click checks before starting continuous navigation.
 */
public final class NavigationPreflightService {
    private final WindowService windowService;
    private final OpenCvNavigationPipeline pipeline;

    public NavigationPreflightService(WindowService windowService) {
        this(windowService, new OpenCvNavigationPipeline(windowService));
    }

    NavigationPreflightService(WindowService windowService, OpenCvNavigationPipeline pipeline) {
        this.windowService = windowService;
        this.pipeline = pipeline;
    }

    public NavigationPreflightResult run(
            VisionConfig config,
            boolean dryRun,
            ClickBackend clickBackend,
            String interceptionHome
    ) {
        List<PreflightItem> items = new ArrayList<>();
        InterceptionBootstrap.configureHome(interceptionHome);

        addDryRun(items, dryRun);
        addWindowTitle(items, config.windowTitle());
        Optional<WindowRef> window = addGameWindow(items, config.windowTitle());
        addMapImage(items, config);
        addArrowTemplate(items, config.arrowTemplate());
        addMiniMapRegion(items, config.miniMapRegion(), window.orElse(null));
        addTarget(items, config.target().x(), config.target().y());
        addClickBackend(items, clickBackend);
        addScreenCalibration(items, config.navigation().screenCalibration());
        if (window.isPresent()) {
            addLiveProbe(items, config, window.get());
        } else {
            items.add(new PreflightItem(
                    "live_probe",
                    "实时定位探测",
                    PreflightLevel.WARN,
                    "跳过：未找到游戏窗口"
            ));
        }

        boolean ready = items.stream().noneMatch(item -> item.level() == PreflightLevel.FAIL);
        return new NavigationPreflightResult(items, ready);
    }

    private static void addDryRun(List<PreflightItem> items, boolean dryRun) {
        if (dryRun) {
            items.add(new PreflightItem(
                    "dry_run",
                    "Dry Run",
                    PreflightLevel.WARN,
                    "已开启：连续导航不会真实点击，请关闭后再跑图"
            ));
        } else {
            items.add(new PreflightItem(
                    "dry_run",
                    "Dry Run",
                    PreflightLevel.OK,
                    "已关闭，将执行真实点击"
            ));
        }
    }

    private static void addWindowTitle(List<PreflightItem> items, String windowTitle) {
        if (windowTitle == null || windowTitle.isBlank()) {
            items.add(new PreflightItem(
                    "window_title",
                    "窗口标题",
                    PreflightLevel.FAIL,
                    "vision.windowTitle 为空"
            ));
            return;
        }
        items.add(new PreflightItem(
                "window_title",
                "窗口标题",
                PreflightLevel.OK,
                "「" + windowTitle.trim() + "」"
        ));
    }

    private Optional<WindowRef> addGameWindow(List<PreflightItem> items, String windowTitle) {
        if (windowTitle == null || windowTitle.isBlank()) {
            return Optional.empty();
        }
        Optional<WindowRef> window = windowService.findWindow(windowTitle);
        if (window.isEmpty()) {
            items.add(new PreflightItem(
                    "game_window",
                    "游戏窗口",
                    PreflightLevel.FAIL,
                    "未找到匹配窗口，请确认游戏已启动且标题正确"
            ));
            return Optional.empty();
        }
        Rectangle bounds = window.get().bounds();
        boolean foreground = windowService.isForeground(window.get());
        items.add(new PreflightItem(
                "game_window",
                "游戏窗口",
                PreflightLevel.OK,
                bounds.width + "×" + bounds.height + " @ (" + bounds.x + "," + bounds.y + ")"
                        + (foreground ? "，已在前台" : "，未在前台（启动导航前请切到游戏）")
        ));
        if (!foreground) {
            items.add(new PreflightItem(
                    "game_foreground",
                    "窗口前台",
                    PreflightLevel.WARN,
                    "游戏窗口当前不在前台"
            ));
        }
        return window;
    }

    private static void addMapImage(List<PreflightItem> items, VisionConfig config) {
        String mapPath = config.mapImage();
        try {
            OpenCvLoader.load();
            Mat map = PathfindingMapLoader.build(config);
            try {
                items.add(new PreflightItem(
                        "map_image",
                        "寻路地图",
                        PreflightLevel.OK,
                        mapPath + " → " + map.cols() + "×" + map.rows() + "（含 mapClosure）"
                ));
            } finally {
                map.release();
            }
        } catch (RuntimeException error) {
            items.add(new PreflightItem(
                    "map_image",
                    "寻路地图",
                    PreflightLevel.FAIL,
                    "无法加载 " + mapPath + "：" + error.getMessage()
            ));
        }
    }

    private static void addArrowTemplate(List<PreflightItem> items, String arrowPath) {
        try {
            OpenCvLoader.load();
            Mat arrow = ImageProcessor.loadMapImage(arrowPath);
            try {
                items.add(new PreflightItem(
                        "arrow_template",
                        "箭头模板",
                        PreflightLevel.OK,
                        arrowPath + " → " + arrow.cols() + "×" + arrow.rows()
                ));
            } finally {
                arrow.release();
            }
        } catch (RuntimeException error) {
            items.add(new PreflightItem(
                    "arrow_template",
                    "箭头模板",
                    PreflightLevel.FAIL,
                    "无法加载 " + arrowPath + "：" + error.getMessage()
            ));
        }
    }

    private static void addMiniMapRegion(
            List<PreflightItem> items,
            RegionConfig region,
            WindowRef window
    ) {
        if (region.width() <= 0 || region.height() <= 0) {
            items.add(new PreflightItem(
                    "minimap_roi",
                    "小地图 ROI",
                    PreflightLevel.FAIL,
                    "宽度或高度无效"
            ));
            return;
        }
        if (region.x() < 0 || region.y() < 0) {
            items.add(new PreflightItem(
                    "minimap_roi",
                    "小地图 ROI",
                    PreflightLevel.FAIL,
                    "坐标不能为负"
            ));
            return;
        }
        String detail = "(" + region.x() + "," + region.y() + ") "
                + region.width() + "×" + region.height();
        if (window == null) {
            items.add(new PreflightItem(
                    "minimap_roi",
                    "小地图 ROI",
                    PreflightLevel.WARN,
                    detail + "（未校验是否落在窗口内）"
            ));
            return;
        }
        Rectangle bounds = window.bounds();
        int maxX = region.x() + region.width();
        int maxY = region.y() + region.height();
        if (maxX > bounds.width || maxY > bounds.height) {
            items.add(new PreflightItem(
                    "minimap_roi",
                    "小地图 ROI",
                    PreflightLevel.FAIL,
                    detail + " 超出窗口 " + bounds.width + "×" + bounds.height
            ));
            return;
        }
        items.add(new PreflightItem(
                "minimap_roi",
                "小地图 ROI",
                PreflightLevel.OK,
                detail
        ));
    }

    private static void addTarget(List<PreflightItem> items, int x, int y) {
        items.add(new PreflightItem(
                "target",
                "目标点",
                PreflightLevel.OK,
                "地图坐标 (" + x + ", " + y + ")"
        ));
    }

    private static void addClickBackend(List<PreflightItem> items, ClickBackend clickBackend) {
        if (clickBackend == ClickBackend.INTERCEPTION) {
            if (InterceptionMouseInput.isAvailable()) {
                items.add(new PreflightItem(
                        "click_backend",
                        "点击后端",
                        PreflightLevel.OK,
                        "Interception 驱动已就绪"
                ));
            } else {
                items.add(new PreflightItem(
                        "click_backend",
                        "点击后端",
                        PreflightLevel.FAIL,
                        "Interception 未就绪：" + InterceptionBootstrap.lastDiagnostic()
                                + "（将回退软件注入，UE 游戏通常无效）"
                ));
            }
            return;
        }
        items.add(new PreflightItem(
                "click_backend",
                "点击后端",
                PreflightLevel.WARN,
                "win32 软件注入；若游戏拦截光标/SendInput，请改用 interception"
        ));
    }

    private static void addScreenCalibration(List<PreflightItem> items, ScreenCalibrationConfig calibration) {
        int count = calibration.points() == null ? 0 : calibration.points().size();
        if (calibration.enabled() && count >= 3) {
            items.add(new PreflightItem(
                    "screen_calibration",
                    "屏幕标定",
                    PreflightLevel.OK,
                    "已启用，标定点 " + count + " 个"
            ));
            return;
        }
        if (calibration.enabled() && count > 0) {
            items.add(new PreflightItem(
                    "screen_calibration",
                    "屏幕标定",
                    PreflightLevel.WARN,
                    "已启用但仅 " + count + " 个点，建议 ≥3"
            ));
            return;
        }
        items.add(new PreflightItem(
                "screen_calibration",
                "屏幕标定",
                PreflightLevel.WARN,
                "未启用（点击落点可能偏差大，建议添加 ≥3 组 map/screen 标定点）"
        ));
    }

    private void addLiveProbe(List<PreflightItem> items, VisionConfig config, WindowRef window) {
        try {
            OpenCvLoader.load();
            pipeline.analyzer().resetLocalizationState();
            Optional<NavigationAnalysis> analysisOptional = pipeline.analyzeNext(config, window, 0);
            NavigationAnalysis analysis = analysisOptional.orElse(null);
            if (analysis == null) {
                items.add(new PreflightItem(
                        "live_probe",
                        "实时定位探测",
                        PreflightLevel.FAIL,
                        "分析返回空"
                ));
                return;
            }
            if (!analysis.success() || analysis.currentMapPoint() == null) {
                items.add(new PreflightItem(
                        "live_probe",
                        "实时定位探测",
                        PreflightLevel.FAIL,
                        analysis.message().isBlank() ? "定位失败" : analysis.message()
                ));
                return;
            }
            double confidence = analysis.localizationConfidence();
            double minConfidence = config.navigation().minLocalizationConfidence();
            PreflightLevel level = confidence >= minConfidence ? PreflightLevel.OK : PreflightLevel.WARN;
            String pathDetail = analysis.path().length == 0
                    ? "，路径为空（可能不可达）"
                    : "，路径航点 " + analysis.path().length;
            items.add(new PreflightItem(
                    "live_probe",
                    "实时定位探测",
                    level,
                    String.format(
                            "地图 (%.0f, %.0f)，置信度 %.2f（阈值 %.2f）%s",
                            analysis.currentMapPoint().x,
                            analysis.currentMapPoint().y,
                            confidence,
                            minConfidence,
                            pathDetail
                    )
            ));
            if (analysis.path().length == 0 && !analysis.arrived()) {
                items.add(new PreflightItem(
                        "live_path",
                        "路径规划",
                        PreflightLevel.FAIL,
                        "当前位置到目标无可行路径，请检查地图密封与障碍阈值"
                ));
            } else if (analysis.arrived()) {
                items.add(new PreflightItem(
                        "live_path",
                        "路径规划",
                        PreflightLevel.OK,
                        "已在目标附近"
                ));
            } else {
                items.add(new PreflightItem(
                        "live_path",
                        "路径规划",
                        PreflightLevel.OK,
                        "路径可用，下一航点 "
                                + formatPoint(analysis.nextMapPoint())
                ));
            }
        } catch (RuntimeException error) {
            items.add(new PreflightItem(
                    "live_probe",
                    "实时定位探测",
                    PreflightLevel.FAIL,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()
            ));
        }
    }

    private static String formatPoint(org.opencv.core.Point point) {
        if (point == null) {
            return "-";
        }
        return "(" + Math.round(point.x) + ", " + Math.round(point.y) + ")";
    }
}
