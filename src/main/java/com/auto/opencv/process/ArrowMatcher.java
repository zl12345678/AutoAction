package com.auto.opencv.process;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArrowMatcher {
    private static final Scalar DEFAULT_ARROW_LOWER_HSV = new Scalar(10, 60, 60);
    private static final Scalar DEFAULT_ARROW_UPPER_HSV = new Scalar(40, 255, 255);
    private static final double MAX_SHAPE_DISTANCE = 0.8;
    /** Golden/yellow arrow pixels are often below 200 in grayscale after color masking. */
    private static final int MASKED_REGION_BINARY_THRESHOLD = 45;
    private static final double MIN_ARROW_AREA_RATIO = 0.0015;
    private static final double MAX_ARROW_AREA_RATIO = 0.35;
    private static final double COLOR_MASK_FALLBACK_CONFIDENCE = 0.55;

    public Point match(Mat smallMap, Mat arrowTemplate) {
        return matchWithConfidence(smallMap, arrowTemplate, DEFAULT_ARROW_LOWER_HSV, DEFAULT_ARROW_UPPER_HSV).center();
    }

    public ArrowMatchResult matchWithConfidence(
            Mat smallMap,
            Mat arrowTemplate,
            int orangeHueMin,
            int orangeHueMax,
            int orangeSatMin,
            int orangeValMin
    ) {
        return matchWithConfidence(
                smallMap,
                arrowTemplate,
                new Scalar(orangeHueMin, orangeSatMin, orangeValMin),
                new Scalar(orangeHueMax, 255, 255)
        );
    }

    public static ArrowMatchResult matchWithConfidence(
            Mat smallMap,
            Mat arrowTemplate,
            Scalar lowerBound,
            Scalar upperBound
    ) {
        return matchWithDebug(smallMap, arrowTemplate, lowerBound, upperBound).result();
    }

    public static ArrowMatchDebug matchWithDebug(
            Mat smallMap,
            Mat arrowTemplate,
            Scalar lowerBound,
            Scalar upperBound
    ) {
        if (smallMap == null || smallMap.empty()) {
            throw new IllegalArgumentException("smallMap must not be empty");
        }
        if (arrowTemplate == null || arrowTemplate.empty()) {
            throw new IllegalArgumentException("arrowTemplate must not be empty");
        }

        Mat colorMask = colorMatch(smallMap, lowerBound, upperBound);
        Mat colorMatchedRegion = new Mat();
        Core.bitwise_and(smallMap, smallMap, colorMatchedRegion, colorMask);
        Mat binaryPreview = toBinary(colorMatchedRegion);

        ArrowMatchResult shapeResult = shapeMatchWithConfidence(colorMatchedRegion, arrowTemplate);
        ArrowMatchResult result = shapeResult.found() ? shapeResult : centroidFromColorMask(colorMask, smallMap);
        return new ArrowMatchDebug(result, colorMask, colorMatchedRegion, binaryPreview);
    }

    public static Point matchArrow(Mat smallMap, Mat arrowTemplate) {
        return matchWithConfidence(smallMap, arrowTemplate, DEFAULT_ARROW_LOWER_HSV, DEFAULT_ARROW_UPPER_HSV).center();
    }

    private static Mat colorMatch(Mat srcImage, Scalar lowerBound, Scalar upperBound) {
        Mat hsvImage = new Mat();
        Imgproc.cvtColor(srcImage, hsvImage, Imgproc.COLOR_BGR2HSV);
        Mat mask = new Mat();
        Core.inRange(hsvImage, lowerBound, upperBound, mask);
        return mask;
    }

    private static ArrowMatchResult shapeMatchWithConfidence(Mat srcImage, Mat templateImage) {
        Mat binarySrc = toBinary(srcImage);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(binarySrc, binarySrc, Imgproc.MORPH_OPEN, kernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(binarySrc, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Mat binaryTemplate = toBinary(templateImage);
        List<MatOfPoint> templateContours = new ArrayList<>();
        Imgproc.findContours(binaryTemplate, templateContours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        if (templateContours.isEmpty()) {
            return new ArrowMatchResult(null, 0.0);
        }

        MatOfPoint templateContour = templateContours.stream()
                .max(Comparator.comparingDouble(Imgproc::contourArea))
                .orElse(templateContours.get(0));

        Point matchedCenter = null;
        double minMatchValue = Double.MAX_VALUE;
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (!isArrowSized(area, srcImage)) {
                continue;
            }
            double matchValue = Imgproc.matchShapes(contour, templateContour, Imgproc.CONTOURS_MATCH_I1, 0);
            if (matchValue < minMatchValue && matchValue < MAX_SHAPE_DISTANCE) {
                minMatchValue = matchValue;
                matchedCenter = contourCenter(contour);
            }
        }
        if (matchedCenter == null) {
            return new ArrowMatchResult(null, 0.0);
        }
        return new ArrowMatchResult(matchedCenter, confidenceFromShapeDistance(minMatchValue));
    }

    private static ArrowMatchResult centroidFromColorMask(Mat colorMask, Mat smallMap) {
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(colorMask, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        if (contours.isEmpty()) {
            return new ArrowMatchResult(null, 0.0);
        }

        MatOfPoint best = null;
        double bestArea = 0.0;
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (!isArrowSized(area, smallMap)) {
                continue;
            }
            if (area > bestArea) {
                bestArea = area;
                best = contour;
            }
        }
        if (best == null) {
            return new ArrowMatchResult(null, 0.0);
        }
        return new ArrowMatchResult(contourCenter(best), COLOR_MASK_FALLBACK_CONFIDENCE);
    }

    private static Point contourCenter(MatOfPoint contour) {
        Moments moments = Imgproc.moments(contour);
        if (moments.m00 != 0.0) {
            return new Point(moments.m10 / moments.m00, moments.m01 / moments.m00);
        }
        Rect boundingRect = Imgproc.boundingRect(contour);
        return new Point(
                boundingRect.x + boundingRect.width / 2.0,
                boundingRect.y + boundingRect.height / 2.0
        );
    }

    private static boolean isArrowSized(double area, Mat image) {
        double imageArea = image.cols() * (double) image.rows();
        if (imageArea <= 0.0) {
            return false;
        }
        double ratio = area / imageArea;
        return ratio >= MIN_ARROW_AREA_RATIO && ratio <= MAX_ARROW_AREA_RATIO;
    }

    static double confidenceFromShapeDistance(double shapeDistance) {
        if (shapeDistance >= MAX_SHAPE_DISTANCE) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - shapeDistance / MAX_SHAPE_DISTANCE));
    }

    private static Mat toBinary(Mat image) {
        Mat gray = new Mat();
        if (image.channels() == 3) {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            image.copyTo(gray);
        }
        Mat binary = new Mat();
        Imgproc.threshold(gray, binary, MASKED_REGION_BINARY_THRESHOLD, 255, Imgproc.THRESH_BINARY);
        return binary;
    }
}
