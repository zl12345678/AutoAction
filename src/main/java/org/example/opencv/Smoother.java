package org.example.opencv;

import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.opencv.imgproc.Imgproc;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

public class Smoother {
    public static void main(String[] args) {
//        时间戳
        long startTime = System.currentTimeMillis();
        // 1. 读取输入图像和模板图像
        Mat source = imread("src/main/resources/template.png");
        Mat template = imread("src/main/resources/input.png");

        if (source.empty() || template.empty()) {
            System.out.println("图像加载失败！");
            return;
        }
        if (source.cols() < template.cols() || source.rows() < template.rows()) {
            System.out.println("错误：源图像尺寸必须大于模板图像！");
            return;
        }
        // 2. 创建结果矩阵
        int resultWidth = source.cols() - template.cols() + 1;
        int resultHeight = source.rows() - template.rows() + 1;
        Mat result = new Mat(resultHeight, resultWidth, CV_32FC1);

        // 3. 执行模板匹配（使用归一化相关系数法）
        opencv_imgproc.matchTemplate(
                source,
                template,
                result,
                opencv_imgproc.TM_CCOEFF_NORMED
        );

        // 4. 找到最佳匹配位置
        DoublePointer minVal = new DoublePointer();
        DoublePointer maxVal = new DoublePointer();
        Point minLoc = new Point();
        Point maxLoc = new Point();
        opencv_core.minMaxLoc(result, minVal, maxVal, minLoc, maxLoc, null);

        Point matchLoc = maxLoc; // 对于TM_CCOEFF_NORMED，最大值是最佳匹配

        // 5. 绘制矩形标记匹配区域
        opencv_imgproc.rectangle(
                source,
                matchLoc,
                new Point(matchLoc.x() + template.cols(), matchLoc.y() + template.rows()),
                new Scalar(0, 255, 0, 0), // BGR颜色：绿色
                2,
                opencv_imgproc.LINE_8,
                0
        );
        //结束时间
        long endTime = System.currentTimeMillis();
        System.out.println("匹配耗时：" + (endTime - startTime) + "毫秒");
        // 6. 保存结果
        imwrite("output_javacv.jpg", source);
        System.out.println("匹配完成，结果保存为 output_javacv.jpg");
    }
    public static void smooth(String filename) {
        Mat image = imread(filename);
        System.out.println(image.cols());
        if (image != null) {
            Mat mat = new Mat();
//            GaussianBlur(image, image, new Size(3, 3), 0);
            cvtColor(image, mat, Imgproc.COLOR_BGR2GRAY);
            imwrite("src/main/resources/222.bmp", mat);
        }
    }
}