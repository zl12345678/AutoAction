package com.auto.opencv.process;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.ORB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 地图匹配器，负责特征检测、特征匹配和仿射变换
 */
public class MapMatcher {
    private Mat largeMap;
    private Mat smallMap;

    public MapMatcher(Mat largeMap, Mat smallMap) {
        this.largeMap = largeMap;
        this.smallMap = smallMap;
    }

    /**
     * 执行地图匹配
     */
    public Mat match() {
        // 初始化ORB特征检测器
        ORB orb = ORB.create();

        // 检测大地图的关键点并计算描述符
        MatOfKeyPoint kpLarge = new MatOfKeyPoint();
        Mat desLarge = new Mat();
        orb.detectAndCompute(largeMap, new Mat(), kpLarge, desLarge);

        // 检测小地图的关键点并计算描述符
        MatOfKeyPoint kpSmall = new MatOfKeyPoint();
        Mat desSmall = new Mat();
        orb.detectAndCompute(smallMap, new Mat(), kpSmall, desSmall);

        // 使用BFMatcher进行特征匹配
        BFMatcher bf = new BFMatcher(Core.NORM_HAMMING, true);
        MatOfDMatch matches = new MatOfDMatch();
        bf.match(desSmall, desLarge, matches);

        // 对匹配结果按距离排序
        List<DMatch> matchesList = matches.toList();
        Collections.sort(matchesList, Comparator.comparingDouble(m -> m.distance));

        // 提取匹配点坐标
        List<Point> srcPoints = new ArrayList<>();
        List<Point> dstPoints = new ArrayList<>();
        for (DMatch match : matchesList) {
            srcPoints.add(kpSmall.toList().get(match.queryIdx).pt); // 小地图中的匹配点
            dstPoints.add(kpLarge.toList().get(match.trainIdx).pt); // 大地图中的匹配点
        }

        // 将匹配点转换为MatOfPoint2f类型
        Mat srcPts = new MatOfPoint2f(srcPoints.toArray(new Point[0]));
        Mat dstPts = new MatOfPoint2f(dstPoints.toArray(new Point[0]));

        // 估计仿射变换矩阵
        Mat M = new Mat();
        Mat inliers = new Mat();
        return Calib3d.estimateAffinePartial2D(srcPts, dstPts, inliers, Calib3d.RANSAC, 3, 2000, 0.99, 10);
    }
    /**
     * 计算大地图和小地图的比例
     *
     * @param M 仿射变换矩阵
     * @return 大地图和小地图的比例
     */
    public double calculateScale(Mat M) {
        if (M == null || M.empty()) {
            throw new IllegalArgumentException("仿射变换矩阵不能为空");
        }

        // 提取缩放系数
        double a11 = M.get(0, 0)[0]; // 第一行第一列
        double a22 = M.get(1, 1)[0]; // 第二行第二列

        // 假设均匀缩放，返回 x 方向的比例
        return a11;
    }

    /**
     * 计算小地图中心在大地图中的坐标
     */
    public Point calculateCenter(Mat M) {
        int h = smallMap.rows();
        int w = smallMap.cols();
        Mat center = new MatOfPoint2f(new Point(w / 2.0, h / 2.0));
        Mat transformedCenter = new Mat();
        Core.transform(center, transformedCenter, M);

        double[] data = transformedCenter.get(0, 0);
        if (data != null && data.length >= 2) {
            return new Point(data[0], data[1]);
        }
        return null;
    }
}
