package com.auto.opencv.process;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.KeyPoint;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.features2d.SIFT;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scale-invariant localization via feature detection, descriptor matching, RANSAC homography.
 */
public final class FeatureHomographyLocalizer {
    private static final double LOWE_RATIO = 0.75;
    private static final int MIN_GOOD_MATCHES_LOCAL = 4;
    private static final int MIN_GOOD_MATCHES_GLOBAL = 8;
    private static final int MIN_INLIERS_LOCAL = 4;
    private static final int MIN_INLIERS_GLOBAL = 4;
    private static final double RANSAC_REPROJ_THRESHOLD = 8.0;
    private static final int ORB_FEATURES = 2000;
    private static final double MIN_INLIER_RATIO = 0.20;

    public enum DetectorKind {
        ORB,
        SIFT
    }

    public record SearchRegion(Mat image, Point originInGlobalMap) {
        public SearchRegion {
            image = image.clone();
        }
    }

    public record MatchOutcome(
            Point mapPointInGlobal,
            double confidence,
            int inlierCount,
            int goodMatchCount,
            MapMatchMethod method
    ) {
        public boolean found() {
            return mapPointInGlobal != null;
        }

        public static MatchOutcome failed() {
            return new MatchOutcome(null, 0.0, 0, 0, MapMatchMethod.NONE);
        }
    }

    private FeatureHomographyLocalizer() {
    }

    public static SearchRegion buildSearchRegion(
            Mat fullLargeMap,
            Mat queryPatch,
            Point priorInGlobalMap,
            int searchRadiusPx
    ) {
        if (fullLargeMap == null || fullLargeMap.empty()) {
            throw new IllegalArgumentException("large map must not be empty");
        }
        if (priorInGlobalMap == null || searchRadiusPx <= 0) {
            return new SearchRegion(fullLargeMap, new Point(0, 0));
        }

        int margin = searchRadiusPx + Math.max(queryPatch.cols(), queryPatch.rows());
        int x = (int) Math.floor(priorInGlobalMap.x) - margin;
        int y = (int) Math.floor(priorInGlobalMap.y) - margin;
        int width = margin * 2 + queryPatch.cols();
        int height = margin * 2 + queryPatch.rows();

        if (x < 0) {
            width += x;
            x = 0;
        }
        if (y < 0) {
            height += y;
            y = 0;
        }
        width = Math.min(width, fullLargeMap.cols() - x);
        height = Math.min(height, fullLargeMap.rows() - y);
        if (width < queryPatch.cols() || height < queryPatch.rows()) {
            return new SearchRegion(fullLargeMap, new Point(0, 0));
        }

        Rect rect = new Rect(x, y, width, height);
        Mat cropped = new Mat(fullLargeMap, rect).clone();
        return new SearchRegion(cropped, new Point(x, y));
    }

    public static MatchOutcome locate(
            Mat queryPatch,
            SearchRegion searchRegion,
            Point queryLocalPoint,
            DetectorKind detector,
            int fullMapCols,
            int fullMapRows,
            boolean constrainedSearch,
            List<String> attempts
    ) {
        if (queryPatch == null || queryPatch.empty() || searchRegion == null || searchRegion.image().empty()) {
            attempts.add(detector + ": 输入图像为空");
            return MatchOutcome.failed();
        }
        if (queryLocalPoint == null) {
            attempts.add(detector + ": 缺少局部坐标");
            return MatchOutcome.failed();
        }

        Mat queryGray = toGray(queryPatch);
        Mat trainGray = toGray(searchRegion.image());

        FeatureData queryFeatures = detectAndCompute(queryGray, detector, attempts, "query");
        FeatureData trainFeatures = detectAndCompute(trainGray, detector, attempts, "train");
        if (queryFeatures == null || trainFeatures == null) {
            return MatchOutcome.failed();
        }

        int minGood = constrainedSearch ? MIN_GOOD_MATCHES_LOCAL : MIN_GOOD_MATCHES_GLOBAL;
        int minInliers = constrainedSearch ? MIN_INLIERS_LOCAL : MIN_INLIERS_GLOBAL;

        List<DMatch> goodMatches = matchDescriptors(queryFeatures, trainFeatures, detector, attempts);
        if (goodMatches.size() < minGood) {
            attempts.add(detector + ": 有效匹配不足 " + goodMatches.size() + "/" + minGood);
            return MatchOutcome.failed();
        }

        List<Point> srcPoints = new ArrayList<>();
        List<Point> dstPoints = new ArrayList<>();
        for (DMatch match : goodMatches) {
            srcPoints.add(queryFeatures.keypoints().get(match.queryIdx).pt);
            dstPoints.add(trainFeatures.keypoints().get(match.trainIdx).pt);
        }

        Mat inliers = new Mat();
        Mat homography = Calib3d.findHomography(
                new MatOfPoint2f(srcPoints.toArray(new Point[0])),
                new MatOfPoint2f(dstPoints.toArray(new Point[0])),
                Calib3d.RANSAC,
                RANSAC_REPROJ_THRESHOLD,
                inliers,
                2000,
                0.995
        );
        if (homography == null || homography.empty()) {
            attempts.add(detector + ": 单应性矩阵估计失败, 尝试仿射回退");
            MatchOutcome affine = locateWithAffinePartial(
                    queryFeatures,
                    trainFeatures,
                    goodMatches,
                    queryLocalPoint,
                    searchRegion,
                    fullMapCols,
                    fullMapRows,
                    detector,
                    attempts,
                    minInliers
            );
            if (affine.found()) {
                return affine;
            }
            return MatchOutcome.failed();
        }

        int inlierCount = Core.countNonZero(inliers);
        if (inlierCount < minInliers) {
            attempts.add(detector + ": RANSAC 内点不足 " + inlierCount + "/" + minInliers);
            return MatchOutcome.failed();
        }

        Point mappedInRegion = perspectiveTransform(homography, queryLocalPoint);
        if (mappedInRegion == null) {
            attempts.add(detector + ": 坐标映射失败");
            return MatchOutcome.failed();
        }

        Point globalPoint = new Point(
                mappedInRegion.x + searchRegion.originInGlobalMap().x,
                mappedInRegion.y + searchRegion.originInGlobalMap().y
        );
        if (!isInside(globalPoint, fullMapCols, fullMapRows)) {
            attempts.add(detector + ": 映射点超出大地图范围");
            return MatchOutcome.failed();
        }

        double confidence = goodMatches.isEmpty() ? 0.0 : (double) inlierCount / goodMatches.size();
        if (confidence < MIN_INLIER_RATIO) {
            attempts.add(detector + ": 内点比例过低 " + String.format("%.2f", confidence));
            return MatchOutcome.failed();
        }

        MapMatchMethod method = detector == DetectorKind.ORB
                ? MapMatchMethod.ORB_HOMOGRAPHY
                : MapMatchMethod.SIFT_HOMOGRAPHY;
        attempts.add(detector + ": 成功 inliers=" + inlierCount
                + "/" + goodMatches.size()
                + ", confidence=" + String.format("%.2f", confidence)
                + ", global=(" + Math.round(globalPoint.x) + "," + Math.round(globalPoint.y) + ")");
        return new MatchOutcome(globalPoint, confidence, inlierCount, goodMatches.size(), method);
    }

    private static FeatureData detectAndCompute(
            Mat gray,
            DetectorKind detector,
            List<String> attempts,
            String label
    ) {
        MatOfKeyPoint keypoints = new MatOfKeyPoint();
        Mat descriptors = new Mat();
        switch (detector) {
            case ORB -> {
                ORB orb = ORB.create(
                        ORB_FEATURES,
                        1.2f,
                        8,
                        31,
                        0,
                        2,
                        ORB.HARRIS_SCORE,
                        31,
                        10
                );
                orb.detectAndCompute(gray, new Mat(), keypoints, descriptors);
            }
            case SIFT -> {
                SIFT sift = SIFT.create();
                sift.detectAndCompute(gray, new Mat(), keypoints, descriptors);
            }
            default -> throw new IllegalArgumentException("Unsupported detector: " + detector);
        }
        if (descriptors.empty() || keypoints.toArray().length < MIN_GOOD_MATCHES_LOCAL) {
            attempts.add(detector + " " + label + " 特征不足: " + keypoints.toArray().length);
            return null;
        }
        return new FeatureData(keypoints.toList(), descriptors);
    }

    private static List<DMatch> matchDescriptors(
            FeatureData query,
            FeatureData train,
            DetectorKind detector,
            List<String> attempts
    ) {
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        if (detector == DetectorKind.ORB) {
            BFMatcher matcher = BFMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING, false);
            matcher.knnMatch(query.descriptors(), train.descriptors(), knnMatches, 2);
        } else {
            DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
            matcher.knnMatch(query.descriptors(), train.descriptors(), knnMatches, 2);
        }

        List<DMatch> goodMatches = new ArrayList<>();
        for (MatOfDMatch pairMat : knnMatches) {
            List<DMatch> pair = pairMat.toList();
            if (pair.size() < 2) {
                continue;
            }
            if (pair.get(0).distance < LOWE_RATIO * pair.get(1).distance) {
                goodMatches.add(pair.get(0));
            }
        }
        goodMatches.sort(Comparator.comparingDouble(match -> match.distance));
        attempts.add(detector + " knn good=" + goodMatches.size() + "/" + knnMatches.size());
        return goodMatches;
    }

    private static MatchOutcome locateWithAffinePartial(
            FeatureData query,
            FeatureData train,
            List<DMatch> goodMatches,
            Point queryLocalPoint,
            SearchRegion searchRegion,
            int fullMapCols,
            int fullMapRows,
            DetectorKind detector,
            List<String> attempts,
            int minInliers
    ) {
        List<Point> srcPoints = new ArrayList<>();
        List<Point> dstPoints = new ArrayList<>();
        for (DMatch match : goodMatches) {
            srcPoints.add(query.keypoints().get(match.queryIdx).pt);
            dstPoints.add(train.keypoints().get(match.trainIdx).pt);
        }
        Mat inliers = new Mat();
        Mat affine = Calib3d.estimateAffinePartial2D(
                new MatOfPoint2f(srcPoints.toArray(new Point[0])),
                new MatOfPoint2f(dstPoints.toArray(new Point[0])),
                inliers,
                Calib3d.RANSAC,
                RANSAC_REPROJ_THRESHOLD,
                2000,
                0.995,
                10
        );
        if (affine == null || affine.empty()) {
            attempts.add(detector + ": 仿射估计失败");
            return MatchOutcome.failed();
        }
        int inlierCount = Core.countNonZero(inliers);
        if (inlierCount < minInliers) {
            attempts.add(detector + ": 仿射内点不足 " + inlierCount + "/" + minInliers);
            return MatchOutcome.failed();
        }
        Point mapped = transformAffine(affine, queryLocalPoint);
        if (mapped == null) {
            return MatchOutcome.failed();
        }
        Point globalPoint = new Point(
                mapped.x + searchRegion.originInGlobalMap().x,
                mapped.y + searchRegion.originInGlobalMap().y
        );
        if (!isInside(globalPoint, fullMapCols, fullMapRows)) {
            return MatchOutcome.failed();
        }
        double confidence = (double) inlierCount / goodMatches.size();
        MapMatchMethod method = detector == DetectorKind.ORB
                ? MapMatchMethod.ORB_HOMOGRAPHY
                : MapMatchMethod.SIFT_HOMOGRAPHY;
        attempts.add(detector + ": 仿射回退成功 inliers=" + inlierCount + "/" + goodMatches.size());
        return new MatchOutcome(globalPoint, confidence, inlierCount, goodMatches.size(), method);
    }

    private static Point transformAffine(Mat affine, Point sourcePoint) {
        if (affine.rows() != 2 || affine.cols() != 3) {
            return null;
        }
        double x = affine.get(0, 0)[0] * sourcePoint.x + affine.get(0, 1)[0] * sourcePoint.y + affine.get(0, 2)[0];
        double y = affine.get(1, 0)[0] * sourcePoint.x + affine.get(1, 1)[0] * sourcePoint.y + affine.get(1, 2)[0];
        return new Point(x, y);
    }

    private static Point perspectiveTransform(Mat homography, Point sourcePoint) {
        MatOfPoint2f src = new MatOfPoint2f(sourcePoint);
        MatOfPoint2f dst = new MatOfPoint2f();
        Core.perspectiveTransform(src, dst, homography);
        Point[] points = dst.toArray();
        if (points.length == 0) {
            return null;
        }
        return points[0];
    }

    private static Mat toGray(Mat image) {
        if (image.channels() == 1) {
            return image.clone();
        }
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        return gray;
    }

    private static boolean isInside(Point point, int cols, int rows) {
        return point.x >= 0 && point.y >= 0 && point.x < cols && point.y < rows;
    }

    private record FeatureData(List<KeyPoint> keypoints, Mat descriptors) {
    }
}
