package org.example.opencv;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

public class MapPreprocessor{

    public static void main(String[] args) {
        Mat src = imread("src/main/resources/111.bmp");
        if (src.empty()) {
            System.err.println("Failed to load image");
            return;
        }

        // 转换为HSV颜色空间（更易处理灰度范围）
        Mat hsv = new Mat();
        opencv_imgproc.cvtColor(src, hsv, opencv_imgproc.COLOR_BGR2HSV);

        // 定义要保留的灰度范围（HSV表示）
        Scalar lowerGray = new Scalar(0, 0, 50,0);   // H:任意, S:0-50, V:50-200
        Scalar upperGray = new Scalar(180, 50, 200,0);

        // 将 Scalar 转换为 Mat
        Mat lowerGrayMat = new Mat(1, 1, opencv_core.CV_8UC3, lowerGray);
        Mat upperGrayMat = new Mat(1, 1, opencv_core.CV_8UC3, upperGray);

        // 创建灰度区域遮罩
        Mat grayMask = new Mat();
        opencv_core.inRange(hsv, lowerGrayMat, upperGrayMat, grayMask);

        // 优化遮罩质量
        Mat kernel = opencv_imgproc.getStructuringElement(
                opencv_imgproc.MORPH_ELLIPSE,
                new Size(5, 5)
        );
        opencv_imgproc.morphologyEx(grayMask, grayMask,
                opencv_imgproc.MORPH_CLOSE, kernel);

        // 应用遮罩
        Mat masked = new Mat();
        src.copyTo(masked, grayMask);

        // 转换为灰度图并二值化
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(masked, gray, opencv_imgproc.COLOR_BGR2GRAY);

        Mat binary = new Mat();
        opencv_imgproc.adaptiveThreshold(
                gray, binary, 255,
                opencv_imgproc.ADAPTIVE_THRESH_MEAN_C,
                opencv_imgproc.THRESH_BINARY_INV,
                11, 10
        );

        imwrite("final_binary.png", binary);

        // 释放资源
        src.release();
        hsv.release();
        lowerGrayMat.release();
        upperGrayMat.release();
        grayMask.release();
        masked.release();
        gray.release();
        binary.release();
    }
}
