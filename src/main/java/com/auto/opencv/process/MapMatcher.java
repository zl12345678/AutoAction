package com.auto.opencv.process;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.features2d.SIFT;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 地图匹配器，负责特征检测、特征匹配和仿射变换
 */
public class MapMatcher {
    private final Mat largeMap;
    private final Mat smallMap;
    // 图像处理时，大地图的二值化阈值，此处为保证大小地图处理一致
    private final int THRESH = 127;
    public MapMatcher(Mat largeMap, Mat smallMap) {
        this.largeMap = largeMap.clone();
        this.smallMap = smallMap.clone();
    }

    /**
     * 执行地图匹配
     */
    public Mat match() {
        // 将图像转换为灰度图
        Mat graySmallMap = new Mat();
        Mat grayLargeMap = new Mat();
        Imgproc.cvtColor(smallMap, graySmallMap, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(largeMap, grayLargeMap, Imgproc.COLOR_BGR2GRAY);

        // 使用 Otsu 方法进行二值化
        Imgproc.threshold(graySmallMap, graySmallMap, THRESH, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        Imgproc.threshold(grayLargeMap, grayLargeMap, THRESH, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        // 使用 SIFT 特征检测器
        SIFT sift = SIFT.create(0, 3, 0.04, 10);
        MatOfKeyPoint kpLarge = new MatOfKeyPoint();
        Mat desLarge = new Mat();
        sift.detectAndCompute(grayLargeMap, new Mat(), kpLarge, desLarge);

        MatOfKeyPoint kpSmall = new MatOfKeyPoint();
        Mat desSmall = new Mat();
        sift.detectAndCompute(graySmallMap, new Mat(), kpSmall, desSmall);

        // 使用 FLANN 匹配器
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(desSmall, desLarge, matches);

        // 筛选匹配点
        List<DMatch> matchesList = matches.toList();
        matchesList.sort(Comparator.comparingDouble(m -> m.distance));
        List<DMatch> goodMatches = matchesList.subList(0, Math.min(50, matchesList.size()));

        // 提取匹配点坐标
        List<Point> srcPoints = new ArrayList<>();
        List<Point> dstPoints = new ArrayList<>();
        for (DMatch match : goodMatches) {
            srcPoints.add(kpSmall.toList().get(match.queryIdx).pt);
            dstPoints.add(kpLarge.toList().get(match.trainIdx).pt);
        }

        // 估计仿射变换矩阵
        Mat M = new Mat();
        Mat inliers = new Mat();
        M = Calib3d.estimateAffinePartial2D(
                new MatOfPoint2f(srcPoints.toArray(new Point[0])),
                new MatOfPoint2f(dstPoints.toArray(new Point[0])),
                inliers,
                Calib3d.RANSAC,
                3, 2000, 0.99, 10
        );

        return M;
    }


    /**
     * 计算小地图中心在大地图中的坐标
     */
    public Point calculateCenter(Mat M) {
        // 检查仿射变换矩阵是否为空
        if (M == null || M.empty()) {
            throw new IllegalArgumentException("仿射变换矩阵不能为空");
        }

        // 检查矩阵维度是否正确
        if (M.rows() != 2 || M.cols() != 3) {
            throw new IllegalArgumentException("仿射变换矩阵必须是 2x3 的矩阵");
        }

        // 计算小地图中心
        int h = smallMap.rows();
        int w = smallMap.cols();
        Mat center = new MatOfPoint2f(new Point(w / 2.0, h / 2.0));

        // 进行坐标变换
        Mat transformedCenter = new Mat();
        Core.transform(center, transformedCenter, M);

        // 检查变换后的坐标数据是否有效
        double[] data = transformedCenter.get(0, 0);
        if (data == null || data.length < 2) {
            return null; // 或抛出异常
        }


        // 返回变换后的坐标
        return new Point(data[0], data[1]);
    }
}
