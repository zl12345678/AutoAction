package com.auto.vision;

import com.auto.config.MapPreprocessConfig;
import com.auto.config.RegionConfig;
import com.auto.config.VisionConfig;
import com.auto.opencv.process.ArrowMatchDebug;
import com.auto.opencv.process.ArrowMatchResult;
import com.auto.opencv.process.ArrowMatcher;
import com.auto.opencv.process.MapMatchDebug;
import com.auto.opencv.process.MapMatchResult;
import com.auto.opencv.process.MapMatcher;
import com.auto.opencv.process.PathPlanner;
import com.auto.opencv.utils.ImageProcessor;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenCvNavigationAnalyzer {
    private final ArrowMatcher arrowMatcher;
    private final LocalizationSmoother localizationSmoother = new LocalizationSmoother();
    private final Map<String, Mat> imageCache = new ConcurrentHashMap<>();
    private final Map<String, Mat> pathfindingMapCache = new ConcurrentHashMap<>();

    public OpenCvNavigationAnalyzer() {
        this(new ArrowMatcher());
    }

    OpenCvNavigationAnalyzer(ArrowMatcher arrowMatcher) {
        this.arrowMatcher = arrowMatcher;
    }

    public void resetLocalizationState() {
        localizationSmoother.reset();
    }

    public void clearMapCaches() {
        imageCache.clear();
        pathfindingMapCache.clear();
    }

    public NavigationAnalysis analyzeWindowCapture(
            VisionConfig config,
            BufferedImage windowCapture,
            Rectangle windowBounds,
            String sourceName
    ) {
        return analyzeWindowCapture(config, windowCapture, windowBounds, windowBounds, sourceName, 0);
    }

    public NavigationAnalysis analyzeWindowCapture(
            VisionConfig config,
            BufferedImage windowCapture,
            Rectangle windowBounds,
            String sourceName,
            int waypointIndex
    ) {
        return analyzeWindowCapture(config, windowCapture, windowBounds, windowBounds, sourceName, waypointIndex);
    }

    public NavigationAnalysis analyzeWindowCapture(
            VisionConfig config,
            BufferedImage windowCapture,
            Rectangle windowBounds,
            Rectangle clientBoundsOnScreen,
            String sourceName,
            int waypointIndex
    ) {
        OpenCvLoader.load();
        ArrowCenteredMiniMapCrop crop = extractArrowCenteredMiniMap(config, windowCapture);
        return analyzeInternal(
                config,
                sourceName,
                windowCapture,
                windowBounds,
                clientBoundsOnScreen,
                crop.miniMapImage(),
                waypointIndex
        );
    }

    public NavigationAnalysis analyzeMiniMap(
            VisionConfig config,
            BufferedImage miniMapImage,
            String sourceName
    ) {
        OpenCvLoader.load();
        return analyzeInternal(config, sourceName, miniMapImage, null, null, miniMapImage, 0);
    }

    public NavigationAnalysis analyzeSample(VisionConfig config) {
        BufferedImage sample = ImageProcessor.loadResourceBufferedImage("img/gamePic.bmp");
        Rectangle bounds = new Rectangle(0, 0, sample.getWidth(), sample.getHeight());
        return analyzeWindowCapture(config, sample, bounds, "resource:img/gamePic.bmp");
    }

    private NavigationAnalysis analyzeInternal(
            VisionConfig config,
            String sourceName,
            BufferedImage sourceImage,
            Rectangle sourceBounds,
            Rectangle clientBoundsOnScreen,
            BufferedImage miniMapImage,
            int waypointIndex
    ) {
        OpenCvLoader.load();

        Mat largeMap = cachedImage(config.mapImage()).clone();
        Mat arrowTemplate = cachedImage(config.arrowTemplate());
        Mat miniMap = ImageProcessor.bufferedImageToMat(miniMapImage);
        Mat miniMapPreview = miniMap.clone();
        Mat mapPreview = largeMap.clone();
        Point targetMapPoint = new Point(config.target().x(), config.target().y());
        drawMapPoint(mapPreview, targetMapPoint, new Scalar(255, 0, 0), 6);
        ScreenMapper screenMapper = new ScreenMapper(config.navigation().screenCalibration());

        Point arrowCenter = null;
        Point rawMapPoint = null;
        Point currentMapPoint = null;
        Point nextMapPoint = null;
        Point nextScreenPoint = null;
        int[][] path = new int[0][2];
        boolean arrived = false;
        double localizationConfidence = 0.0;
        LocalizationMethod localizationMethod = LocalizationMethod.NONE;
        LocalizationResult localizationResult = null;

        try {
            MapPreprocessConfig preprocess = config.mapPreprocess();
            ArrowMatchResult arrowMatch = arrowMatcher.matchWithConfidence(
                    miniMap,
                    arrowTemplate,
                    preprocess.orangeHueMin(),
                    preprocess.orangeHueMax(),
                    preprocess.orangeSatMin(),
                    preprocess.orangeValMin()
            );
            arrowCenter = arrowMatch.center();
            if (!arrowMatch.found()) {
                localizationResult = localizationSmoother.correct(
                        null,
                        0.0,
                        config.navigation(),
                        config.moveStep()
                );
                Point predicted = localizationResult.acceptedPoint();
                if (predicted != null) {
                    currentMapPoint = predicted;
                    localizationConfidence = localizationResult.effectiveConfidence();
                    localizationMethod = localizationResult.method();
                } else {
                    return buildResult(
                            sourceName,
                            sourceImage,
                            miniMapImage,
                            ImageProcessor.matToBufferedImage(miniMapPreview),
                            ImageProcessor.matToBufferedImage(mapPreview),
                            buildClickPreview(sourceImage, sourceBounds, null, screenMapper, config),
                            arrowCenter,
                            null,
                            targetMapPoint,
                            null,
                            null,
                            path,
                            false,
                            false,
                            "未在小地图中识别到角色箭头，请检查截图区域或箭头模板。",
                            localizationConfidence,
                            localizationMethod,
                            localizationResult
                    );
                }
            } else {
                Rect matchRect = matchAreaRect(miniMap, arrowCenter, config.matchAreaSize());
                Imgproc.rectangle(
                        miniMapPreview,
                        new Point(matchRect.x, matchRect.y),
                        new Point(matchRect.x + matchRect.width, matchRect.y + matchRect.height),
                        new Scalar(0, 255, 0),
                        2
                );
                drawMapPoint(miniMapPreview, arrowCenter, new Scalar(0, 255, 255), 5);

                Mat matchArea = new Mat(miniMap, matchRect).clone();
                Point arrowInPatch = arrowInMatchArea(arrowCenter, matchRect);
                MapMatchResult mapMatch = locateOnLargeMap(
                        config,
                        largeMap,
                        matchArea,
                        arrowInPatch,
                        preprocess,
                        localizationSmoother.lastKnownPoint()
                );
                rawMapPoint = mapMatch.mapPoint();
                if (rawMapPoint == null && !isArrowCenteredMiniMap(miniMap, config.matchAreaSize())) {
                    int expandedSize = (int) Math.round(config.matchAreaSize() * 1.1);
                    Rect expandedRect = centeredRect(miniMap, arrowCenter, expandedSize, expandedSize);
                    Mat expandedArea = new Mat(miniMap, expandedRect).clone();
                    mapMatch = locateOnLargeMap(
                            config,
                            largeMap,
                            expandedArea,
                            arrowInMatchArea(arrowCenter, expandedRect),
                            preprocess,
                            localizationSmoother.lastKnownPoint()
                    );
                    rawMapPoint = mapMatch.mapPoint();
                    if (rawMapPoint != null) {
                        matchRect = expandedRect;
                    }
                }

                if (rawMapPoint == null) {
                    localizationResult = localizationSmoother.correct(
                            null,
                            0.0,
                            config.navigation(),
                            config.moveStep()
                    );
                    Point predicted = localizationResult.acceptedPoint();
                    if (predicted != null) {
                        currentMapPoint = predicted;
                        localizationConfidence = localizationResult.effectiveConfidence();
                        localizationMethod = localizationResult.method();
                    } else {
                        return buildResult(
                                sourceName,
                                sourceImage,
                                miniMapImage,
                                ImageProcessor.matToBufferedImage(miniMapPreview),
                                ImageProcessor.matToBufferedImage(mapPreview),
                                buildClickPreview(sourceImage, sourceBounds, null, screenMapper, config),
                                arrowCenter,
                                null,
                                targetMapPoint,
                                null,
                                null,
                                path,
                                false,
                                false,
                                "大地图匹配失败，当前小地图特征不足以定位角色位置。",
                                localizationConfidence,
                                localizationMethod,
                                localizationResult
                        );
                    }
                } else {
                    double mapMatchConfidence = mapMatch.confidence();
                    localizationConfidence = Math.min(1.0, (arrowMatch.confidence() + mapMatchConfidence) / 2.0);
                    localizationResult = localizationSmoother.correct(
                            rawMapPoint,
                            localizationConfidence,
                            config.navigation(),
                            config.moveStep()
                    );
                    currentMapPoint = localizationResult.acceptedPoint();
                    localizationConfidence = localizationResult.effectiveConfidence();
                    localizationMethod = localizationResult.method();
                    drawLocalizationOnMap(mapPreview, localizationResult, matchRect);
                }
            }

            if (ImageProcessor.getDistance(currentMapPoint, targetMapPoint) <= config.arriveDistance()) {
                arrived = true;
                return buildResult(
                        sourceName,
                        sourceImage,
                        miniMapImage,
                        ImageProcessor.matToBufferedImage(miniMapPreview),
                        ImageProcessor.matToBufferedImage(mapPreview),
                        buildClickPreview(sourceImage, sourceBounds, null, screenMapper, config),
                        arrowCenter,
                        currentMapPoint,
                        targetMapPoint,
                        targetMapPoint,
                        null,
                        path,
                        true,
                        true,
                        "已到达目标点附近，无需继续点击。",
                        localizationConfidence,
                        localizationMethod,
                        localizationResult
                );
            }

            Mat pathfindingMap = cachedPathfindingMap(config);
            try {
                path = new PathPlanner(pathfindingMap, currentMapPoint, targetMapPoint, config.obstacleThreshold())
                        .findPath();
            } finally {
                pathfindingMap.release();
            }
            if (path.length == 0) {
                return buildResult(
                        sourceName,
                        sourceImage,
                        miniMapImage,
                        ImageProcessor.matToBufferedImage(miniMapPreview),
                        ImageProcessor.matToBufferedImage(mapPreview),
                        buildClickPreview(sourceImage, sourceBounds, null, screenMapper, config),
                        arrowCenter,
                        currentMapPoint,
                        targetMapPoint,
                        null,
                        null,
                        path,
                        false,
                        false,
                        "路径规划失败，起点和终点之间没有可用通路。",
                        localizationConfidence,
                        localizationMethod,
                        localizationResult
                );
            }

            drawPath(mapPreview, path);
            nextMapPoint = chooseNextMapPoint(
                    currentMapPoint,
                    targetMapPoint,
                    path,
                    config.navigation().waypointReachDistance(),
                    waypointIndex
            );
            drawMapPoint(mapPreview, nextMapPoint, new Scalar(0, 255, 255), 5);

            if (sourceBounds != null) {
                nextScreenPoint = resolveScreenPoint(
                        config,
                        sourceBounds,
                        clientBoundsOnScreen,
                        currentMapPoint,
                        nextMapPoint,
                        screenMapper
                );
            }

            String message = "分析完成：当前位置 "
                    + pointText(currentMapPoint)
                    + "，下一点击地图点 "
                    + pointText(nextMapPoint)
                    + (nextScreenPoint == null ? "" : "，预测屏幕点击点 " + pointText(nextScreenPoint))
                    + "，路径点数 " + path.length
                    + "，置信度 " + String.format("%.2f", localizationConfidence)
                    + (localizationResult != null && !localizationResult.detail().isEmpty()
                    ? "；" + localizationResult.detail()
                    : "")
                    + "。";
            return buildResult(
                    sourceName,
                    sourceImage,
                    miniMapImage,
                    ImageProcessor.matToBufferedImage(miniMapPreview),
                    ImageProcessor.matToBufferedImage(mapPreview),
                    buildClickPreview(sourceImage, sourceBounds, nextScreenPoint, screenMapper, config),
                    arrowCenter,
                    currentMapPoint,
                    targetMapPoint,
                    nextMapPoint,
                    nextScreenPoint,
                    path,
                    false,
                    true,
                    message,
                    localizationConfidence,
                    localizationMethod,
                    localizationResult
            );
        } catch (RuntimeException e) {
            return buildResult(
                    sourceName,
                    sourceImage,
                    miniMapImage,
                    ImageProcessor.matToBufferedImage(miniMapPreview),
                    ImageProcessor.matToBufferedImage(mapPreview),
                    buildClickPreview(sourceImage, sourceBounds, nextScreenPoint, screenMapper, config),
                    arrowCenter,
                    currentMapPoint,
                    targetMapPoint,
                    nextMapPoint,
                    nextScreenPoint,
                    path,
                    arrived,
                    false,
                    "分析过程异常: " + e.getMessage(),
                    localizationConfidence,
                    localizationMethod,
                    localizationResult
            );
        }
    }

    /**
     * Crops a square minimap from the window with the player arrow at the center.
     * Side length is {@link VisionConfig#matchAreaSize()} (expected 80).
     */
    public ArrowCenteredMiniMapCrop extractArrowCenteredMiniMap(VisionConfig config, BufferedImage source) {
        OpenCvLoader.load();
        RegionConfig region = config.miniMapRegion();
        int cropSize = config.matchAreaSize();
        Mat sourceMat = ImageProcessor.bufferedImageToMat(source);
        validateRegionOnSource(region, source.getWidth(), source.getHeight());

        Mat roiMiniMap = ImageProcessor.extractRegion(
                sourceMat,
                new Rect(region.x(), region.y(), region.width(), region.height())
        ).clone();
        MapPreprocessConfig preprocess = config.mapPreprocess();
        ArrowMatchResult arrowMatch = arrowMatcher.matchWithConfidence(
                roiMiniMap,
                cachedImage(config.arrowTemplate()),
                preprocess.orangeHueMin(),
                preprocess.orangeHueMax(),
                preprocess.orangeSatMin(),
                preprocess.orangeValMin()
        );
        if (!arrowMatch.found() || arrowMatch.center() == null) {
            throw new IllegalArgumentException("未在小地图 ROI 中识别到箭头，无法裁剪以箭头为中心的小地图。");
        }

        Point arrowInSource = new Point(
                region.x() + arrowMatch.center().x,
                region.y() + arrowMatch.center().y
        );
        Rect cropRect = centeredRect(sourceMat, arrowInSource, cropSize, cropSize);
        Mat cropped = new Mat(sourceMat, cropRect).clone();
        Point arrowInCrop = new Point(
                arrowInSource.x - cropRect.x,
                arrowInSource.y - cropRect.y
        );
        Rectangle sourceCropBounds = new Rectangle(cropRect.x, cropRect.y, cropRect.width, cropRect.height);
        return new ArrowCenteredMiniMapCrop(
                ImageProcessor.matToBufferedImage(cropped),
                arrowInCrop,
                arrowMatch.confidence(),
                sourceCropBounds
        );
    }

    public BufferedImage extractMiniMap(VisionConfig config, BufferedImage source) {
        return extractArrowCenteredMiniMap(config, source).miniMapImage();
    }

    private static void validateRegionOnSource(RegionConfig region, int sourceWidth, int sourceHeight) {
        int x = region.x();
        int y = region.y();
        int width = region.width();
        int height = region.height();
        if (x < 0 || y < 0 || width <= 0 || height <= 0
                || x + width > sourceWidth || y + height > sourceHeight) {
            throw new IllegalArgumentException("小地图 ROI 超出源图范围，请重新框选。源图 "
                    + sourceWidth + "x" + sourceHeight
                    + "，ROI (" + x + ", " + y + ", " + width + ", " + height + ")");
        }
    }

    public ArrowMatchResult detectArrow(VisionConfig config, BufferedImage miniMapImage) {
        return detectArrowWithDebug(config, miniMapImage).result();
    }

    public ArrowDetectResult detectArrowWithDebug(VisionConfig config, BufferedImage miniMapImage) {
        OpenCvLoader.load();
        Mat miniMap = ImageProcessor.bufferedImageToMat(miniMapImage);
        Mat arrowTemplate = cachedImage(config.arrowTemplate());
        var preprocess = config.mapPreprocess();
        ArrowMatchDebug debug = ArrowMatcher.matchWithDebug(
                miniMap,
                arrowTemplate,
                new Scalar(preprocess.orangeHueMin(), preprocess.orangeSatMin(), preprocess.orangeValMin()),
                new Scalar(preprocess.orangeHueMax(), 255, 255)
        );

        Mat miniMapPreview = miniMap.clone();
        if (debug.result().found() && debug.result().center() != null) {
            drawMapPoint(miniMapPreview, debug.result().center(), new Scalar(0, 255, 255), 5);
        }

        List<NavigationDebugArtifact> artifacts = List.of(
                NavigationDebugArtifacts.image("minimap", "小地图", miniMapImage),
                NavigationDebugArtifacts.mat("arrow_color_mask", "箭头颜色掩码", debug.colorMask()),
                NavigationDebugArtifacts.mat("arrow_color_isolated", "箭头颜色区域", debug.colorIsolated()),
                NavigationDebugArtifacts.mat("arrow_binary", "箭头二值图", debug.binaryPreview()),
                NavigationDebugArtifacts.image("minimap_arrow_marked", "箭头标注", ImageProcessor.matToBufferedImage(miniMapPreview))
        );
        return new ArrowDetectResult(debug.result(), NavigationDebugArtifacts.nonEmpty(artifacts));
    }

    public LocateResult locateOnMap(VisionConfig config, BufferedImage miniMapImage, Point arrowCenter) {
        return locateOnMapWithDebug(config, miniMapImage, arrowCenter).toLocateResult();
    }

    public LocateOnMapDebug locateOnMapWithDebug(VisionConfig config, BufferedImage miniMapImage, Point arrowCenter) {
        OpenCvLoader.load();
        Mat largeMap = cachedImage(config.mapImage()).clone();
        Mat miniMap = ImageProcessor.bufferedImageToMat(miniMapImage);
        Mat miniMapPreview = miniMap.clone();
        Mat mapPreview = largeMap.clone();
        Point targetMapPoint = new Point(config.target().x(), config.target().y());
        drawMapPoint(mapPreview, targetMapPoint, new Scalar(255, 0, 0), 6);

        Rect matchRect = matchAreaRect(miniMap, arrowCenter, config.matchAreaSize());
        Imgproc.rectangle(
                miniMapPreview,
                new Point(matchRect.x, matchRect.y),
                new Point(matchRect.x + matchRect.width, matchRect.y + matchRect.height),
                new Scalar(0, 255, 0),
                2
        );
        drawMapPoint(miniMapPreview, arrowCenter, new Scalar(0, 255, 255), 5);

        Mat matchArea = new Mat(miniMap, matchRect).clone();
        Point arrowInPatch = arrowInMatchArea(arrowCenter, matchRect);
        MapPreprocessConfig preprocess = config.mapPreprocess();
        List<NavigationDebugArtifact> artifacts = new ArrayList<>();
        artifacts.add(NavigationDebugArtifacts.image("minimap_marked", "小地图+匹配框", ImageProcessor.matToBufferedImage(miniMapPreview)));

        MapMatchDebug mapMatchDebug = MapMatcher.forLocalizationWithDebug(
                largeMap,
                matchArea,
                preprocess,
                arrowInPatch,
                arrowInPatch,
                localizationSmoother.lastKnownPoint(),
                localizationSearchRadius(config)
        );
        MapMatchResult mapMatch = snapToWalkable(config, mapMatchDebug.result());
        collectMapMatchArtifacts(artifacts, mapMatchDebug);

        Point rawMapPoint = mapMatch.mapPoint();
        if (rawMapPoint == null && !isArrowCenteredMiniMap(miniMap, config.matchAreaSize())) {
            int expandedSize = (int) Math.round(config.matchAreaSize() * 1.1);
            Rect expandedRect = centeredRect(miniMap, arrowCenter, expandedSize, expandedSize);
            mapMatchDebug = MapMatcher.forLocalizationWithDebug(
                    largeMap,
                    new Mat(miniMap, expandedRect).clone(),
                    preprocess,
                    arrowInMatchArea(arrowCenter, expandedRect),
                    arrowInMatchArea(arrowCenter, expandedRect),
                    localizationSmoother.lastKnownPoint(),
                    localizationSearchRadius(config)
            );
            mapMatch = snapToWalkable(config, mapMatchDebug.result());
            collectMapMatchArtifacts(artifacts, mapMatchDebug);
            rawMapPoint = mapMatch.mapPoint();
            if (rawMapPoint != null) {
                matchRect = expandedRect;
            }
        }
        if (rawMapPoint == null) {
            artifacts.add(NavigationDebugArtifacts.image("large_map_input", "大地图输入", ImageProcessor.matToBufferedImage(largeMap)));
            String detail = String.join("; ", mapMatchDebug.attempts());
            return LocateOnMapDebug.failed(
                    "大地图匹配失败，当前小地图特征不足以定位角色位置。诊断: " + detail,
                    NavigationDebugArtifacts.nonEmpty(artifacts),
                    ImageProcessor.matToBufferedImage(mapPreview)
            );
        }

        double confidence = mapMatch.confidence();
        LocalizationResult localizationResult = localizationSmoother.correct(
                rawMapPoint,
                confidence,
                config.navigation(),
                config.moveStep()
        );
        Point currentMapPoint = localizationResult.acceptedPoint();
        drawLocalizationOnMap(mapPreview, localizationResult, matchRect);
        artifacts.add(NavigationDebugArtifacts.image("map_locate_result", "大地图定位结果", ImageProcessor.matToBufferedImage(mapPreview)));

        String detailSuffix = localizationResult.detail().isEmpty() ? "" : "；" + localizationResult.detail();
        NavigationAnalysis analysis = buildResult(
                "",
                null,
                miniMapImage,
                ImageProcessor.matToBufferedImage(miniMapPreview),
                ImageProcessor.matToBufferedImage(mapPreview),
                null,
                arrowCenter,
                currentMapPoint,
                targetMapPoint,
                null,
                null,
                new int[0][2],
                false,
                true,
                "大地图定位成功: " + pointText(currentMapPoint)
                        + "（" + mapMatch.method() + ", 置信度 " + String.format("%.2f", confidence) + "）"
                        + detailSuffix,
                localizationResult.effectiveConfidence(),
                localizationResult.method(),
                localizationResult
        );
        return new LocateOnMapDebug(
                new LocateResult(true, analysis.message(), currentMapPoint, confidence, analysis, artifacts),
                NavigationDebugArtifacts.nonEmpty(artifacts)
        );
    }

    private static void collectMapMatchArtifacts(List<NavigationDebugArtifact> artifacts, MapMatchDebug debug) {
        artifacts.add(NavigationDebugArtifacts.mat("match_patch_raw", "匹配块(原图)", debug.patchRaw()));
        artifacts.add(NavigationDebugArtifacts.mat("match_patch_prepared", "匹配块(预处理)", debug.patchPrepared()));
        artifacts.add(NavigationDebugArtifacts.mat("large_map_prepared", "大地图(预处理)", debug.largeMapPrepared()));
        artifacts.add(NavigationDebugArtifacts.mat("match_patch_inverted", "匹配块(反色)", debug.patchPreparedInverted()));
        artifacts.add(NavigationDebugArtifacts.mat("template_heatmap", "模板匹配热力图", debug.templateHeatmap()));
    }

    public PathPlanResult planPath(VisionConfig config, Point currentMapPoint, int waypointIndex) {
        OpenCvLoader.load();
        Mat largeMap = cachedImage(config.mapImage()).clone();
        Mat mapPreview = largeMap.clone();
        Point targetMapPoint = new Point(config.target().x(), config.target().y());
        drawMapPoint(mapPreview, targetMapPoint, new Scalar(255, 0, 0), 6);
        drawMapPoint(mapPreview, currentMapPoint, new Scalar(0, 0, 255), 6);

        if (ImageProcessor.getDistance(currentMapPoint, targetMapPoint) <= config.arriveDistance()) {
            NavigationAnalysis analysis = buildResult(
                    "",
                    null,
                    null,
                    null,
                    ImageProcessor.matToBufferedImage(mapPreview),
                    null,
                    null,
                    currentMapPoint,
                    targetMapPoint,
                    targetMapPoint,
                    null,
                    new int[0][2],
                    true,
                    true,
                    "已到达目标点附近。",
                    1.0,
                    LocalizationMethod.MAP_MATCH
            );
            return new PathPlanResult(true, analysis.message(), new int[0][2], targetMapPoint, analysis);
        }

        Mat pathfindingMap = cachedPathfindingMap(config);
        int[][] path;
        try {
            path = new PathPlanner(pathfindingMap, currentMapPoint, targetMapPoint, config.obstacleThreshold()).findPath();
        } finally {
            pathfindingMap.release();
        }
        if (path.length == 0) {
            return new PathPlanResult(false, "路径规划失败，起点和终点之间没有可用通路。", new int[0][2], null, null);
        }
        drawPath(mapPreview, path);
        Point nextMapPoint = chooseNextMapPoint(
                currentMapPoint,
                targetMapPoint,
                path,
                config.navigation().waypointReachDistance(),
                waypointIndex
        );
        drawMapPoint(mapPreview, nextMapPoint, new Scalar(0, 255, 255), 5);
        NavigationAnalysis analysis = buildResult(
                "",
                null,
                null,
                null,
                ImageProcessor.matToBufferedImage(mapPreview),
                null,
                null,
                currentMapPoint,
                targetMapPoint,
                nextMapPoint,
                null,
                path,
                false,
                true,
                "路径规划成功，路径点数 " + path.length + "。",
                1.0,
                LocalizationMethod.MAP_MATCH
        );
        return new PathPlanResult(true, analysis.message(), path, nextMapPoint, analysis);
    }

    public ScreenMapResult resolveScreenMapping(
            VisionConfig config,
            Rectangle sourceBounds,
            Rectangle clientBoundsOnScreen,
            Point currentMapPoint,
            Point nextMapPoint,
            NavigationAnalysis base
    ) {
        ScreenMapper screenMapper = new ScreenMapper(config.navigation().screenCalibration());
        Point nextScreenPoint = resolveScreenPoint(
                config,
                sourceBounds,
                clientBoundsOnScreen,
                currentMapPoint,
                nextMapPoint,
                screenMapper
        );
        BufferedImage clickPreview = buildClickPreview(
                base.sourceImage(),
                sourceBounds,
                nextScreenPoint,
                screenMapper,
                config
        );
        NavigationAnalysis analysis = buildResult(
                base.sourceName(),
                base.sourceImage(),
                base.miniMapImage(),
                base.miniMapPreviewImage(),
                base.mapPreviewImage(),
                clickPreview,
                base.arrowCenter(),
                currentMapPoint,
                base.targetMapPoint(),
                nextMapPoint,
                nextScreenPoint,
                base.path(),
                base.arrived(),
                true,
                "屏幕映射完成: " + pointText(nextScreenPoint),
                base.localizationConfidence(),
                base.localizationMethod(),
                localizationResultFrom(base, currentMapPoint)
        );
        return new ScreenMapResult(nextScreenPoint, screenMapper.enabled(), analysis);
    }

    private static LocalizationResult localizationResultFrom(NavigationAnalysis base, Point acceptedPoint) {
        if (base.localizationRawPoint() == null
                && !base.localizationOutlierRejected()
                && base.localizationDetail().isEmpty()) {
            return null;
        }
        return new LocalizationResult(
                acceptedPoint,
                base.localizationRawPoint(),
                base.localizationOutlierRejected(),
                base.localizationConfidence(),
                base.localizationMethod(),
                base.localizationDetail()
        );
    }

    public NavigationAnalysis buildMiniMapPreviewAnalysis(
            VisionConfig config,
            BufferedImage sourceImage,
            Rectangle sourceBounds,
            String sourceName,
            BufferedImage miniMapImage,
            Point arrowCenter,
            double arrowConfidence
    ) {
        OpenCvLoader.load();
        Mat miniMap = ImageProcessor.bufferedImageToMat(miniMapImage);
        Mat miniMapPreview = miniMap.clone();
        if (arrowCenter != null) {
            Rect matchRect = matchAreaRect(miniMap, arrowCenter, config.matchAreaSize());
            Imgproc.rectangle(
                    miniMapPreview,
                    new Point(matchRect.x, matchRect.y),
                    new Point(matchRect.x + matchRect.width, matchRect.y + matchRect.height),
                    new Scalar(0, 255, 0),
                    2
            );
            drawMapPoint(miniMapPreview, arrowCenter, new Scalar(0, 255, 255), 5);
        }
        Point targetMapPoint = new Point(config.target().x(), config.target().y());
        return buildResult(
                sourceName,
                sourceImage,
                miniMapImage,
                ImageProcessor.matToBufferedImage(miniMapPreview),
                null,
                null,
                arrowCenter,
                null,
                targetMapPoint,
                null,
                null,
                new int[0][2],
                false,
                arrowCenter != null,
                arrowCenter != null ? "箭头已识别" : "小地图已裁剪",
                arrowConfidence,
                arrowCenter != null ? LocalizationMethod.ARROW_TEMPLATE : LocalizationMethod.NONE
        );
    }

    public record ArrowCenteredMiniMapCrop(
            BufferedImage miniMapImage,
            Point arrowCenter,
            double arrowConfidence,
            Rectangle sourceCropBounds
    ) {
    }

    public record ArrowDetectResult(
            ArrowMatchResult result,
            List<NavigationDebugArtifact> artifacts
    ) {
        public ArrowDetectResult {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }

    public record LocateOnMapDebug(
            LocateResult locateResult,
            List<NavigationDebugArtifact> artifacts
    ) {
        public LocateOnMapDebug {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }

        public LocateResult toLocateResult() {
            return locateResult;
        }

        public static LocateOnMapDebug failed(String message, List<NavigationDebugArtifact> artifacts, BufferedImage mapPreview) {
            NavigationAnalysis analysis = mapPreview == null ? null : new NavigationAnalysis(
                    "",
                    null,
                    null,
                    null,
                    mapPreview,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new int[0][2],
                    false,
                    false,
                    message,
                    0.0,
                    LocalizationMethod.NONE
            );
            return new LocateOnMapDebug(
                    new LocateResult(false, message, null, 0.0, analysis, artifacts),
                    artifacts
            );
        }
    }

    public record LocateResult(
            boolean success,
            String message,
            Point currentMapPoint,
            double confidence,
            NavigationAnalysis analysis,
            List<NavigationDebugArtifact> artifacts
    ) {
        public LocateResult {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }

    public record PathPlanResult(
            boolean success,
            String message,
            int[][] path,
            Point nextMapPoint,
            NavigationAnalysis analysis
    ) {
    }

    public record ScreenMapResult(
            Point screenPoint,
            boolean usedCalibration,
            NavigationAnalysis analysis
    ) {
    }

    static Point resolveScreenPoint(
            VisionConfig config,
            Rectangle sourceBounds,
            Rectangle clientBoundsOnScreen,
            Point currentMapPoint,
            Point nextMapPoint,
            ScreenMapper screenMapper
    ) {
        Point mapped = screenMapper.mapToScreen(nextMapPoint);
        if (mapped != null) {
            return new Point(sourceBounds.x + mapped.x, sourceBounds.y + mapped.y);
        }
        Rectangle playArea = clientBoundsOnScreen == null ? sourceBounds : clientBoundsOnScreen;
        Point playCenter = new Point(playArea.getCenterX(), playArea.getCenterY());
        return ScreenMapper.fallbackScreenPoint(currentMapPoint, nextMapPoint, playCenter, config.moveStep());
    }

    private NavigationAnalysis buildResult(
            String sourceName,
            BufferedImage sourceImage,
            BufferedImage miniMapImage,
            BufferedImage miniMapPreviewImage,
            BufferedImage mapPreviewImage,
            BufferedImage clickPreviewImage,
            Point arrowCenter,
            Point currentMapPoint,
            Point targetMapPoint,
            Point nextMapPoint,
            Point nextScreenPoint,
            int[][] path,
            boolean arrived,
            boolean success,
            String message,
            double localizationConfidence,
            LocalizationMethod localizationMethod
    ) {
        return buildResult(
                sourceName,
                sourceImage,
                miniMapImage,
                miniMapPreviewImage,
                mapPreviewImage,
                clickPreviewImage,
                arrowCenter,
                currentMapPoint,
                targetMapPoint,
                nextMapPoint,
                nextScreenPoint,
                path,
                arrived,
                success,
                message,
                localizationConfidence,
                localizationMethod,
                null
        );
    }

    private NavigationAnalysis buildResult(
            String sourceName,
            BufferedImage sourceImage,
            BufferedImage miniMapImage,
            BufferedImage miniMapPreviewImage,
            BufferedImage mapPreviewImage,
            BufferedImage clickPreviewImage,
            Point arrowCenter,
            Point currentMapPoint,
            Point targetMapPoint,
            Point nextMapPoint,
            Point nextScreenPoint,
            int[][] path,
            boolean arrived,
            boolean success,
            String message,
            double localizationConfidence,
            LocalizationMethod localizationMethod,
            LocalizationResult localizationResult
    ) {
        Point rawPoint = localizationResult == null ? null : localizationResult.rawPoint();
        boolean outlierRejected = localizationResult != null && localizationResult.outlierRejected();
        String localizationDetail = localizationResult == null ? "" : localizationResult.detail();
        return new NavigationAnalysis(
                sourceName,
                sourceImage,
                miniMapImage,
                miniMapPreviewImage,
                mapPreviewImage,
                clickPreviewImage,
                arrowCenter,
                currentMapPoint,
                targetMapPoint,
                nextMapPoint,
                nextScreenPoint,
                path,
                arrived,
                success,
                message,
                localizationConfidence,
                localizationMethod,
                rawPoint,
                outlierRejected,
                localizationDetail
        );
    }

    private Mat cachedImage(String resourcePath) {
        return imageCache.computeIfAbsent(resourcePath, ImageProcessor::loadMapImage);
    }

    private Mat cachedPathfindingMap(VisionConfig config) {
        String key = PathfindingMapLoader.cacheKey(config);
        Mat cached = pathfindingMapCache.get(key);
        if (cached != null) {
            return cached.clone();
        }
        Mat built = PathfindingMapLoader.build(config);
        pathfindingMapCache.put(key, built);
        return built.clone();
    }

    private MapMatchResult locateOnLargeMap(
            VisionConfig config,
            Mat largeMap,
            Mat matchArea,
            Point arrowInMatchArea,
            MapPreprocessConfig preprocess,
            Point priorHint
    ) {
        MapMatchResult raw = MapMatcher.forLocalizationWithDebug(
                largeMap,
                matchArea,
                preprocess,
                arrowInMatchArea,
                arrowInMatchArea,
                priorHint,
                localizationSearchRadius(config)
        ).result();
        return snapToWalkable(config, raw);
    }

    private MapMatchResult snapToWalkable(VisionConfig config, MapMatchResult raw) {
        if (!raw.found() || raw.mapPoint() == null) {
            return raw;
        }
        Mat pathfindingMap = cachedPathfindingMap(config);
        try {
            int snapRadius = Math.max(12, config.moveStep() / 3);
            Point snapped = LocalizationRefiner.snapToWalkable(
                    pathfindingMap,
                    raw.mapPoint(),
                    config.obstacleThreshold(),
                    snapRadius
            );
            if (snapped.equals(raw.mapPoint())) {
                return raw;
            }
            return new MapMatchResult(snapped, raw.confidence() * 0.98, raw.method());
        } finally {
            pathfindingMap.release();
        }
    }

    private static int localizationSearchRadius(VisionConfig config) {
        double maxJump = config.navigation().maxLocalizationJumpPx();
        if (maxJump > 0) {
            return (int) Math.ceil(maxJump);
        }
        return (int) Math.ceil(config.moveStep() * 4.0);
    }

    private static Point arrowInMatchArea(Point arrowCenter, Rect matchRect) {
        return new Point(arrowCenter.x - matchRect.x, arrowCenter.y - matchRect.y);
    }

    static Point chooseNextMapPoint(
            Point current,
            Point target,
            int[][] path,
            double minDistance,
            int startIndex
    ) {
        int fromIndex = Math.max(0, Math.min(startIndex, path.length - 1));
        for (int i = fromIndex; i < path.length; i++) {
            Point candidate = new Point(path[i][0], path[i][1]);
            if (ImageProcessor.getDistance(current, candidate) > minDistance) {
                return candidate;
            }
        }
        return target;
    }

    private static boolean isArrowCenteredMiniMap(Mat miniMap, int cropSize) {
        return miniMap.cols() == cropSize && miniMap.rows() == cropSize;
    }

    private static Rect matchAreaRect(Mat miniMap, Point arrowCenter, int matchAreaSize) {
        if (isArrowCenteredMiniMap(miniMap, matchAreaSize)) {
            return new Rect(0, 0, miniMap.cols(), miniMap.rows());
        }
        return centeredRect(miniMap, arrowCenter, matchAreaSize, matchAreaSize);
    }

    private static Rect centeredRect(Mat mat, Point center, int size) {
        return centeredRect(mat, center, size, size);
    }

    private static Rect centeredRect(Mat mat, Point center, int width, int height) {
        int halfW = width / 2;
        int halfH = height / 2;
        int x = (int) Math.round(center.x) - halfW;
        int y = (int) Math.round(center.y) - halfH;
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x + width > mat.cols()) {
            x = Math.max(0, mat.cols() - width);
        }
        if (y + height > mat.rows()) {
            y = Math.max(0, mat.rows() - height);
        }
        int cropWidth = Math.min(width, mat.cols() - x);
        int cropHeight = Math.min(height, mat.rows() - y);
        return new Rect(x, y, Math.max(1, cropWidth), Math.max(1, cropHeight));
    }

    private static void drawLocalizationOnMap(Mat mapPreview, LocalizationResult localization, Rect matchRect) {
        if (localization == null || localization.acceptedPoint() == null) {
            return;
        }
        Point rawPoint = localization.rawPoint();
        Point acceptedPoint = localization.acceptedPoint();
        if (rawPoint != null) {
            drawMapPoint(mapPreview, rawPoint, new Scalar(0, 165, 255), 4);
            if (localization.outlierRejected()) {
                drawLocalizationCorrection(mapPreview, rawPoint, acceptedPoint);
            }
        }
        drawMapPoint(mapPreview, acceptedPoint, new Scalar(0, 0, 255), 6);
        drawMatchArea(mapPreview, acceptedPoint, matchRect.width, matchRect.height);
    }

    private static void drawLocalizationCorrection(Mat mapPreview, Point rawPoint, Point acceptedPoint) {
        Imgproc.line(mapPreview, rawPoint, acceptedPoint, new Scalar(0, 0, 255), 2);
        Imgproc.putText(
                mapPreview,
                "rejected",
                new Point(rawPoint.x + 6, rawPoint.y - 6),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.45,
                new Scalar(0, 0, 255),
                1
        );
    }

    private static void drawPath(Mat mapPreview, int[][] path) {
        for (int i = 1; i < path.length; i++) {
            Point start = new Point(path[i - 1][0], path[i - 1][1]);
            Point end = new Point(path[i][0], path[i][1]);
            Imgproc.line(mapPreview, start, end, new Scalar(0, 255, 0), 1);
        }
    }

    private static void drawMapPoint(Mat mapPreview, Point point, Scalar color, int radius) {
        if (point != null) {
            Imgproc.circle(mapPreview, point, radius, color, -1);
        }
    }

    private static void drawMatchArea(Mat mapPreview, Point center, int width, int height) {
        Point topLeft = new Point(center.x - width / 2.0, center.y - height / 2.0);
        Point bottomRight = new Point(topLeft.x + width, topLeft.y + height);
        Imgproc.rectangle(mapPreview, topLeft, bottomRight, new Scalar(255, 255, 0), 2);
    }

    private static BufferedImage buildClickPreview(
            BufferedImage sourceImage,
            Rectangle sourceBounds,
            Point nextScreenPoint,
            ScreenMapper screenMapper,
            VisionConfig config
    ) {
        if (sourceImage == null) {
            return null;
        }

        BufferedImage preview = ImageProcessor.copyBufferedImage(sourceImage);
        Graphics2D graphics = preview.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setStroke(new BasicStroke(4f));

        if (sourceBounds != null) {
            int centerX = (int) Math.round(sourceBounds.getWidth() / 2.0);
            int centerY = (int) Math.round(sourceBounds.getHeight() / 2.0);
            graphics.setColor(new Color(80, 180, 255));
            graphics.drawOval(centerX - 10, centerY - 10, 20, 20);

            if (screenMapper.enabled()) {
                graphics.setColor(new Color(120, 220, 120));
                List<com.auto.config.ScreenCalibrationPointConfig> points =
                        config.navigation().screenCalibration().points();
                for (com.auto.config.ScreenCalibrationPointConfig point : points) {
                    graphics.fillOval((int) point.screenX() - 4, (int) point.screenY() - 4, 8, 8);
                }
            }

            if (nextScreenPoint != null) {
                int relativeX = (int) Math.round(nextScreenPoint.x - sourceBounds.x);
                int relativeY = (int) Math.round(nextScreenPoint.y - sourceBounds.y);
                graphics.setColor(new Color(255, 220, 0));
                graphics.drawLine(centerX, centerY, relativeX, relativeY);
                graphics.fillOval(relativeX - 8, relativeY - 8, 16, 16);
                graphics.drawOval(relativeX - 20, relativeY - 20, 40, 40);
            }
        }
        graphics.dispose();
        return preview;
    }

    private static String pointText(Point point) {
        if (point == null) {
            return "-";
        }
        return "(" + Math.round(point.x) + ", " + Math.round(point.y) + ")";
    }
}
