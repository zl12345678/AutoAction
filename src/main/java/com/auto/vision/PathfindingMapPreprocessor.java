package com.auto.vision;



import com.auto.config.MapPreprocessConfig;

import com.auto.config.RegionConfig;

import com.auto.opencv.utils.ImageProcessor;

import org.opencv.core.Mat;

import org.opencv.core.Rect;

import org.opencv.imgproc.Imgproc;



import java.awt.image.BufferedImage;



/**

 * Extracts the in-game map from a window capture and produces a binary pathfinding map (no boundary sealing).

 * Use {@link PathfindingMapClosureAnalyzer} to test whether the map is closed.

 */

public final class PathfindingMapPreprocessor {



    public MapPreprocessResult process(BufferedImage source, MapPreprocessConfig config) {

        if (source == null) {

            return new MapPreprocessResult(null, null, "缺少源图像。");

        }

        OpenCvLoader.load();



        RegionConfig region = config.mapRegion();

        Mat sourceMat = ImageProcessor.bufferedImageToMat(source);

        Rect roi = new Rect(region.x(), region.y(), region.width(), region.height());

        Mat cropped = ImageProcessor.extractRegion(sourceMat, roi).clone();

        if (cropped.empty()) {

            return new MapPreprocessResult(null, null, "地图 ROI 超出截图范围或为空，请调整 mapRegion。");

        }



        BufferedImage originalMapImage = ImageProcessor.matToBufferedImage(cropped);

        Mat working = cropped.clone();

        if (config.removeOrangeMarkers()) {

            MapMarkerRemover.removeColorRange(working, config.orangeRange());

        }



        Mat pathfindingMat;

        if (config.binaryEnabled()) {

            Mat gray = new Mat();

            Imgproc.cvtColor(working, gray, Imgproc.COLOR_BGR2GRAY);

            pathfindingMat = new Mat();

            if (config.useOtsu()) {

                Imgproc.threshold(gray, pathfindingMat, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

            } else {

                Imgproc.threshold(gray, pathfindingMat, config.threshold(), 255, Imgproc.THRESH_BINARY);

            }

        } else {

            pathfindingMat = new Mat();

            Imgproc.cvtColor(working, pathfindingMat, Imgproc.COLOR_BGR2GRAY);

        }



        BufferedImage pathfindingMapImage = ImageProcessor.matToBufferedImage(pathfindingMat);

        String mode = config.binaryEnabled()

                ? (config.useOtsu() ? "Otsu 二值化" : "阈值二值化(" + config.threshold() + ")")

                : "灰度图";

        String markers = config.removeOrangeMarkers() ? "，已去除橙色标记" : "";

        return new MapPreprocessResult(

                originalMapImage,

                pathfindingMapImage,

                "地图处理完成：" + mode + markers + "，尺寸 "

                        + originalMapImage.getWidth() + "x" + originalMapImage.getHeight()

                        + "。请用「闭合检测」验证边界。"

        );

    }

}


