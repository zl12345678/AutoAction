package com.auto.opencv.process;

import com.auto.opencv.utils.GameWindowClicker;
import com.auto.opencv.utils.Visualizer;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArrowMatcher {
    static {
        // 加载OpenCV本地库
        URL url = ClassLoader.getSystemResource("lib/opencv/opencv_java4110.dll");
        System.load(url.getPath());
    }

    public static void main(String[] args) {
        // 加载小地图和箭头模板
        Mat arrowTemplate = Imgcodecs.imread("src/main/resources/img/arrow_template2.bmp",Imgcodecs.IMREAD_COLOR);
        Visualizer visualizer = new Visualizer(new Mat(),"aaa");
        while (true){
        try {

                // 捕获游戏窗口截图
                GameWindowClicker gameWindowClicker = new GameWindowClicker("Torchlight: Infinite  ");
                BufferedImage gameWindowImage = gameWindowClicker.captureGameWindow(gameWindowClicker.getGameWindowX(),
                        gameWindowClicker.getGameWindowY(),
                        gameWindowClicker.getGameWindowWidth(),
                        gameWindowClicker.getGameWindowHeight());
                if (gameWindowImage == null) {
                    throw new RuntimeException("无法捕获游戏窗口截图");
                }

                // 将 BufferedImage 转换为 OpenCV 的 Mat
                Mat gameWindow = bufferedImageToMat(gameWindowImage);


                // 截取小地图区域
                Mat smallMap = extractSmallMap(gameWindow, new Rect(0, 100, 200, 200));
                // 匹配箭头
                Point arrowCenter = matchArrow(smallMap, arrowTemplate);

                // 水平拼接图像
//                Mat combined = new Mat();
//                Core.hconcat(Arrays.asList(smallMap, smallMap), combined);
//                HighGui.imshow("ad",combined);
            visualizer.updateDisplayMat(smallMap);
            visualizer.drawPoint(arrowCenter, new Scalar(0, 255, 0));
                visualizer.showResult();
//            HighGui.imshow("windowName", combined);
//            HighGui.waitKey(1);
//            visualizer.showResult();
                System.out.println("Arrow Center: " + arrowCenter);

        } catch (Exception e){
            e.printStackTrace();
        }
        finally {
//            visualizer.close();
        }
        }

    }
    // 截取小地图区域
    private static Mat extractSmallMap(Mat gameWindow, Rect roi) {
        return new Mat(gameWindow, roi);
    }
    private static Mat bufferedImageToMat(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Mat mat = new Mat(height, width, CvType.CV_8UC3); // 3通道的Mat
        byte[] data;

        if (image.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            // 直接使用 DataBufferByte
            data = ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData();
        } else if (image.getType() == BufferedImage.TYPE_INT_ARGB || image.getType() == BufferedImage.TYPE_INT_RGB) {
            // 处理 DataBufferInt（ARGB/RGB -> BGR）
            data = new byte[width * height * 3];
            int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[y * width + x];
                    data[(y * width + x) * 3] = (byte) (pixel & 0xFF); // B
                    data[(y * width + x) * 3 + 1] = (byte) ((pixel >> 8) & 0xFF); // G
                    data[(y * width + x) * 3 + 2] = (byte) ((pixel >> 16) & 0xFF); // R
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported BufferedImage type: " + image.getType());
        }

        // 将字节数组放入Mat
        mat.put(0, 0, data);
        return mat;
    }

    // 匹配箭头
    public static Point matchArrow(Mat smallMap, Mat arrowTemplate) {
        // 颜色匹配
        Mat colorMask = colorMatch(smallMap, new Scalar(20, 100, 100), new Scalar(30, 255, 255));
        Mat colorMatchedRegion = new Mat();
        Core.bitwise_and(smallMap, smallMap, colorMatchedRegion, colorMask);

        // 形状匹配
        Point shapeMatchedCenter = shapeMatch(colorMatchedRegion, arrowTemplate);
        return shapeMatchedCenter;
        // 特征点匹配
      /*  Point featurePointCenter = templateMatch(colorMatchedRegion, arrowTemplate);

        // 结合形状和特征点匹配结果
        if (shapeMatchedCenter != null && featurePointCenter != null) {
            return new Point(
                    (shapeMatchedCenter.x + featurePointCenter.x) / 2,
                    (shapeMatchedCenter.y + featurePointCenter.y) / 2
            );
        }
        return null;*/
    }

    // 颜色匹配
    private static Mat colorMatch(Mat srcImage, Scalar lowerBound, Scalar upperBound) {
        Mat hsvImage = new Mat();
        Imgproc.cvtColor(srcImage, hsvImage, Imgproc.COLOR_BGR2HSV);
        Mat mask = new Mat();
        Core.inRange(hsvImage, lowerBound, upperBound, mask);
        return mask;
    }

    // 形状匹配
    private static Point shapeMatch(Mat srcImage, Mat templateImage) {
        // 灰度化
        Mat graySrc = new Mat();
        Imgproc.cvtColor(srcImage, graySrc, Imgproc.COLOR_BGR2GRAY);
        Mat binarySrc = new Mat();
        Imgproc.threshold(graySrc, binarySrc, 200, 255, Imgproc.THRESH_BINARY);
                // 提取轮廓
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binarySrc, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // 模板图像灰度化
        Mat grayTemplate = new Mat();
        Imgproc.cvtColor(templateImage, grayTemplate, Imgproc.COLOR_BGR2GRAY);
        Mat binaryTemplate = new Mat();
        Imgproc.threshold(grayTemplate, binaryTemplate, 200, 255, Imgproc.THRESH_BINARY);
// 形态学操作（开运算去除噪声）
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(binarySrc, binarySrc, Imgproc.MORPH_OPEN, kernel);

        // 提取模板轮廓
        List<MatOfPoint> templateContours = new ArrayList<>();
        Imgproc.findContours(binaryTemplate, templateContours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        MatOfPoint templateContour = templateContours.get(0);

        // 形状匹配
        Point matchedCenter = null;
        double minMatchValue = Double.MAX_VALUE;
        for (MatOfPoint contour : contours) {
            double matchValue = Imgproc.matchShapes(contour, templateContour, Imgproc.CONTOURS_MATCH_I1, 0);
            if (matchValue < minMatchValue && matchValue < 0.8) {
                minMatchValue = matchValue;
                Rect boundingRect = Imgproc.boundingRect(contour);
                matchedCenter = new Point(boundingRect.x + boundingRect.width / 2, boundingRect.y + boundingRect.height / 2);
            }
        }
        return matchedCenter;
    }

    // 特征点匹配
    private static Point templateMatch(Mat srcImage, Mat templateImage) {
        // 检查模板图像是否为空
        if (templateImage.empty()) {
            throw new RuntimeException("模板图像为空");
        }

        // 对模板图像进行预处理
        Mat grayTemplate = new Mat();
        Imgproc.cvtColor(templateImage, grayTemplate, Imgproc.COLOR_BGR2GRAY);
        Mat binaryTemplate = new Mat();
        Imgproc.threshold(grayTemplate, binaryTemplate, 127, 255, Imgproc.THRESH_BINARY);

        // 使用 ORB 特征点检测器
        ORB orb = ORB.create(
                500,      // 增加最大特征点数量
                1.2f,     // 金字塔尺度因子
                8,        // 金字塔层数
                31,       // 边界阈值
                0,        // 第一层
                2,        // WTA_K
                ORB.HARRIS_SCORE, // 评分类型
                31,       // 描述符窗口大小
                10        // FAST 检测阈值
        );
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
        Mat descriptors1 = new Mat();
        Mat descriptors2 = new Mat();
        orb.detectAndCompute(srcImage, new Mat(), keypoints1, descriptors1);
        orb.detectAndCompute(binaryTemplate, new Mat(), keypoints2, descriptors2); // 使用预处理后的模板图像

        // 检查描述符是否为空
        if (descriptors1.empty() || descriptors2.empty()) {
            throw new RuntimeException("未检测到特征点：descriptors1.empty() = " + descriptors1.empty() + ", descriptors2.empty() = " + descriptors2.empty());
        }

        // 检查描述符类型
        if (descriptors1.type() != CvType.CV_8U) {
            descriptors1.convertTo(descriptors1, CvType.CV_8U);
        }
        if (descriptors2.type() != CvType.CV_8U) {
            descriptors2.convertTo(descriptors2, CvType.CV_8U);
        }

        // 检查描述符列数
        if (descriptors1.cols() != descriptors2.cols()) {
            throw new RuntimeException("描述符列数不一致：descriptors1.cols() = " + descriptors1.cols() + ", descriptors2.cols() = " + descriptors2.cols());
        }

        // 使用 BFMatcher 进行匹配
        BFMatcher matcher = new BFMatcher(Core.NORM_HAMMING, false);
        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(descriptors1, descriptors2, matches);

        // 筛选匹配点
        List<DMatch> goodMatches = new ArrayList<>();
        for (DMatch match : matches.toList()) {
            if (match.distance < 80) { // 调整此阈值
                goodMatches.add(match);
            }
        }

        // 计算匹配中心
        double xSum = 0, ySum = 0;
        int count = 0;
        for (DMatch match : goodMatches) {
            Point pt = keypoints1.toList().get(match.queryIdx).pt;
            xSum += pt.x;
            ySum += pt.y;
            count++;
        }

        if (count > 0) {
            return new Point(xSum / count, ySum / count);
        }
        return null;
    }




}
