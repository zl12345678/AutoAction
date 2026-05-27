package com.auto.opencv.process;

import com.auto.config.MapPreprocessConfig;
import com.auto.vision.MapAlignmentPreprocessor;
import com.auto.vision.MinimapPatchCrop;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * Aligns a minimap patch to a pathfinding map using scale-invariant feature matching
 * (ORB preferred, SIFT fallback) and homography with RANSAC.
 */
public class MapMatcher {
    private static final double MIN_ACCEPT_CONFIDENCE = 0.30;

    private final Mat fullLargeMap;
    private final Mat smallMap;
    private final int searchRadiusPx;
    private final double minConfidenceThreshold;

    public MapMatcher(Mat largeMap, Mat smallMap) {
        this(largeMap, smallMap, MapPreprocessConfig.defaults());
    }

    private MapMatcher(Mat largeMap, Mat smallMap, MapPreprocessConfig config) {
        this(largeMap, smallMap, config, 0);
    }

    private MapMatcher(Mat largeMap, Mat smallMap, MapPreprocessConfig config, int searchRadiusPx) {
        this.fullLargeMap = largeMap.clone();
        this.smallMap = smallMap.clone();
        this.searchRadiusPx = Math.max(0, searchRadiusPx);
        this.minConfidenceThreshold = Math.max(MIN_ACCEPT_CONFIDENCE, config.minTemplateScore() - 0.15);
    }

    public static MapMatcher forLocalization(Mat largeMap, Mat minimapPatch, MapPreprocessConfig config) {
        return forLocalization(largeMap, minimapPatch, config, null);
    }

    public static MapMatcher forLocalization(
            Mat largeMap,
            Mat minimapPatch,
            MapPreprocessConfig config,
            Point markerInPatch
    ) {
        return forLocalization(
                largeMap,
                minimapPatch,
                config,
                markerInPatch,
                MapAlignmentPreprocessor.AlignmentFeatureMode.AUTO
        );
    }

    public static MapMatcher forLocalization(
            Mat largeMap,
            Mat minimapPatch,
            MapPreprocessConfig config,
            Point markerInPatch,
            MapAlignmentPreprocessor.AlignmentFeatureMode mode
    ) {
        return forLocalization(largeMap, minimapPatch, config, markerInPatch, mode, 0);
    }

    public static MapMatcher forLocalization(
            Mat largeMap,
            Mat minimapPatch,
            MapPreprocessConfig config,
            Point markerInPatch,
            MapAlignmentPreprocessor.AlignmentFeatureMode mode,
            int searchRadiusPx
    ) {
        return forFeatureLocalization(largeMap, minimapPatch, config, markerInPatch, searchRadiusPx);
    }

    public static MapMatcher forFeatureLocalization(
            Mat largeMap,
            Mat minimapPatch,
            MapPreprocessConfig config,
            Point markerInPatch,
            int searchRadiusPx
    ) {
        Mat preparedLarge = MapAlignmentPreprocessor.prepareLargeMapForFeatureMatching(largeMap, config);
        Mat preparedPatch = MapAlignmentPreprocessor.prepareMinimapPatchForFeatureMatching(
                minimapPatch,
                config,
                markerInPatch
        );
        return new MapMatcher(preparedLarge, preparedPatch, config, searchRadiusPx);
    }

    public MapMatchResult locate(Point localPoint) {
        return locate(localPoint, null);
    }

    public MapMatchResult locate(Point localPoint, Point priorHint) {
        return locateWithDebug(localPoint, priorHint).result();
    }

    public MapMatchDebug locateWithDebug(Point localPoint) {
        return locateWithDebug(localPoint, null);
    }

    public MapMatchDebug locateWithDebug(Point localPoint, Point priorHint) {
        if (localPoint == null) {
            return new MapMatchDebug(MapMatchResult.failed(), null, null, null, null, null, List.of("缺少局部坐标"));
        }

        Mat patchPreparedInverted = new Mat();
        Core.bitwise_not(smallMap, patchPreparedInverted);

        List<String> attempts = new ArrayList<>();
        FeatureHomographyLocalizer.SearchRegion searchRegion = FeatureHomographyLocalizer.buildSearchRegion(
                fullLargeMap,
                smallMap,
                priorHint,
                searchRadiusPx
        );
        if (priorHint != null && searchRadiusPx > 0) {
            attempts.add("局部搜索区域 origin=("
                    + Math.round(searchRegion.originInGlobalMap().x) + ","
                    + Math.round(searchRegion.originInGlobalMap().y) + ") size="
                    + searchRegion.image().cols() + "x" + searchRegion.image().rows());
        } else {
            attempts.add("全图特征搜索");
        }

        MapMatchResult best = tryFeatureLocate(localPoint, smallMap, searchRegion, priorHint, attempts);
        if (!best.found()) {
            attempts.add("反色 patch 重试");
            best = tryFeatureLocate(localPoint, patchPreparedInverted, searchRegion, priorHint, attempts);
        }

        if (!best.found()) {
            attempts.add("ORB/SIFT 特征匹配均失败");
            return new MapMatchDebug(
                    MapMatchResult.failed(),
                    null,
                    smallMap.clone(),
                    fullLargeMap.clone(),
                    patchPreparedInverted,
                    null,
                    attempts
            );
        }

        attempts.add("采用 " + best.method() + ", confidence=" + String.format("%.2f", best.confidence()));
        return new MapMatchDebug(
                best,
                null,
                smallMap.clone(),
                fullLargeMap.clone(),
                patchPreparedInverted,
                null,
                attempts
        );
    }

    private MapMatchResult tryFeatureLocate(
            Point localPoint,
            Mat queryPatch,
            FeatureHomographyLocalizer.SearchRegion searchRegion,
            Point priorHint,
            List<String> attempts
    ) {
        boolean constrainedSearch = priorHint != null && searchRadiusPx > 0;
        FeatureHomographyLocalizer.MatchOutcome orb = FeatureHomographyLocalizer.locate(
                queryPatch,
                searchRegion,
                localPoint,
                FeatureHomographyLocalizer.DetectorKind.ORB,
                fullLargeMap.cols(),
                fullLargeMap.rows(),
                constrainedSearch,
                attempts
        );
        if (orb.found() && orb.confidence() >= minConfidenceThreshold) {
            return toResult(orb);
        }

        FeatureHomographyLocalizer.MatchOutcome sift = FeatureHomographyLocalizer.locate(
                queryPatch,
                searchRegion,
                localPoint,
                FeatureHomographyLocalizer.DetectorKind.SIFT,
                fullLargeMap.cols(),
                fullLargeMap.rows(),
                constrainedSearch,
                attempts
        );
        if (sift.found()) {
            if (orb.found() && orb.confidence() > sift.confidence()) {
                attempts.add("ORB 置信度更高，保留 ORB 结果");
                return toResult(orb);
            }
            return toResult(sift);
        }

        if (orb.found()) {
            attempts.add("回退使用低置信 ORB");
            return toResult(orb);
        }
        return MapMatchResult.failed();
    }

    private static MapMatchResult toResult(FeatureHomographyLocalizer.MatchOutcome outcome) {
        return new MapMatchResult(outcome.mapPointInGlobal(), outcome.confidence(), outcome.method());
    }

    public static MapMatchDebug forLocalizationWithDebug(
            Mat largeMap,
            Mat minimapPatch,
            MapPreprocessConfig config,
            Point localPoint
    ) {
        return forLocalizationWithDebug(largeMap, minimapPatch, config, localPoint, localPoint, null, 0);
    }

    public static MapMatchDebug forLocalizationWithDebug(
            Mat largeMap,
            Mat minimapPatch,
            MapPreprocessConfig config,
            Point localPoint,
            Point markerInPatch,
            Point priorHint
    ) {
        return forLocalizationWithDebug(largeMap, minimapPatch, config, localPoint, markerInPatch, priorHint, 0);
    }

    public static MapMatchDebug forLocalizationWithDebug(
            Mat largeMap,
            Mat minimapPatch,
            MapPreprocessConfig config,
            Point localPoint,
            Point markerInPatch,
            Point priorHint,
            int searchRadiusPx
    ) {
        List<String> attempts = new ArrayList<>();
        Rect fullPatch = new Rect(0, 0, minimapPatch.cols(), minimapPatch.rows());
        MinimapPatchCrop crop = new MinimapPatchCrop(minimapPatch, markerInPatch, fullPatch);
        Point croppedLocal = localPoint;
        Point croppedMarker = markerInPatch;
        attempts.add("featurePatch=" + crop.sourceRect().width + "x" + crop.sourceRect().height);

        MapMatcher matcher = forFeatureLocalization(largeMap, crop.patch(), config, croppedMarker, searchRadiusPx);
        MapMatchDebug debug = matcher.locateWithDebug(croppedLocal, priorHint);
        attempts.add("特征预处理=grayscale_clahe");
        attempts.addAll(debug.attempts());

        return new MapMatchDebug(
                debug.result(),
                crop.patch().clone(),
                matcher.smallMap.clone(),
                matcher.fullLargeMap.clone(),
                debug.patchPreparedInverted(),
                null,
                attempts
        );
    }

    /**
     * Legacy helper retained for manual experiments; uses ORB homography, not template matching.
     */
    public double matchByResize(Mat largeMap, Mat smallMap, double[] scales) {
        MapPreprocessConfig config = MapPreprocessConfig.defaults();
        double bestConfidence = 0.0;
        Point center = new Point(smallMap.cols() / 2.0, smallMap.rows() / 2.0);
        for (double scale : scales) {
            Mat resized = new Mat();
            org.opencv.imgproc.Imgproc.resize(largeMap, resized, new org.opencv.core.Size(), scale, scale);
            MapMatcher matcher = new MapMatcher(resized, smallMap, config);
            MapMatchResult result = matcher.locate(center);
            if (result.found()) {
                bestConfidence = Math.max(bestConfidence, result.confidence());
            }
        }
        return bestConfidence > 0 ? 1.0 - bestConfidence : 1.0;
    }

}
