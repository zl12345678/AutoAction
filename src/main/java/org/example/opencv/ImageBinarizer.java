package org.example.opencv;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class ImageBinarizer {
    // 高斯模糊核大小（奇数）
    private int blurKernelSize = 5;
    // 二值化类型（默认Otsu自动阈值）
    private int thresholdType = Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU;

    // 全参数构造器
    public ImageBinarizer(int blurKernelSize, int thresholdType) {
        validateKernelSize(blurKernelSize);
        this.blurKernelSize = blurKernelSize;
        this.thresholdType = thresholdType;
    }

    // 默认构造器
    public ImageBinarizer() {}

    private void validateKernelSize(int size) {
        if (size % 2 == 0) {
            throw new IllegalArgumentException("Kernel size must be odd number");
        }
    }

    /**
     * 自动二值化处理流程
     * @param src 输入BGR彩色图像
     * @return 二值化后的Mat（单通道）
     */
    public Mat binarize(Mat src) {
        if (src.empty()) {
            throw new IllegalArgumentException("Input image is empty");
        }

        Mat gray = new Mat();
        Mat blurred = new Mat();
        Mat binary = new Mat();

        try {
            // 转换为灰度图
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

            // 高斯模糊降噪
            Imgproc.GaussianBlur(gray, blurred,
                    new Size(blurKernelSize, blurKernelSize), 0);

            // 自动阈值二值化（Otsu算法）
            Imgproc.threshold(blurred, binary, 0, 255, thresholdType);

            return binary;
        } finally {
            // 释放中间Mat对象
            gray.release();
            blurred.release();
        }
    }

    // Getter/Setter 省略...
}
