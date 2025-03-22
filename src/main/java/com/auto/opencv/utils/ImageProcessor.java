// src/main/java/com/auto/opencv/utils/ImageProcessor.java
package com.auto.opencv.utils;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.awt.image.BufferedImage;

/**
 *  图片处理工具类
 */
public class ImageProcessor {
    
    // BufferedImage转Mat对象
    public static Mat bufferedImageToMat(BufferedImage image) {
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

    // 加载图像文件
    public static Mat loadImage(String path) {
        Mat image = Imgcodecs.imread(path, Imgcodecs.IMREAD_COLOR);
        if (image.empty()) {
            throw new RuntimeException("无法加载图像: " + path);
        }
        return image;
    }

    // 截取指定区域
    public static Mat extractRegion(Mat src, Rect roi) {
        return new Mat(src, roi);
    }

    // 截取中心区域
    public static Mat extractCenterArea(Mat src, Point center, int size) {
        Rect roi = new Rect((int) (center.x - size / 2), (int) (center.y - size / 2), size, size);
        return new Mat(src, roi);
    }
    /**
     * 计算两点之间的距离
     * @param p1
     * @param p2
     * @return
     */
    public static double getDistance(Point p1, Point p2) {
        double x = p1.x - p2.x;
        double y = p1.y - p2.y;
        return Math.sqrt(x * x + y * y);
    }
}
