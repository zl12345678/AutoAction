// src/main/java/com/auto/opencv/utils/ImageProcessor.java
package com.auto.opencv.utils;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 *  图片处理工具类
 */
public class ImageProcessor {
    
    // BufferedImage转Mat对象
    public static Mat bufferedImageToMat(BufferedImage image) {
        BufferedImage normalized = image;
        if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            normalized = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D graphics = normalized.createGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
        }

        int width = normalized.getWidth();
        int height = normalized.getHeight();
        Mat mat = new Mat(height, width, CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) normalized.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        return mat;
    }

    public static BufferedImage matToBufferedImage(Mat source) {
        if (source == null || source.empty()) {
            throw new IllegalArgumentException("Mat must not be empty");
        }

        Mat display = source;
        int type;
        if (source.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        } else if (source.channels() == 3) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        } else if (source.channels() == 4) {
            display = new Mat();
            Imgproc.cvtColor(source, display, Imgproc.COLOR_BGRA2BGR);
            type = BufferedImage.TYPE_3BYTE_BGR;
        } else {
            throw new IllegalArgumentException("Unsupported Mat channels: " + source.channels());
        }

        BufferedImage image = new BufferedImage(display.cols(), display.rows(), type);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        display.get(0, 0, data);
        return image;
    }

    public static BufferedImage copyBufferedImage(BufferedImage image) {
        int type = image.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_ARGB : image.getType();
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), type);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    /**
     * ARGB canvas required for semi-transparent overlay drawing on grayscale maps.
     */
    public static BufferedImage toArgbOverlayCanvas(BufferedImage source) {
        BufferedImage canvas = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return canvas;
    }

    public static BufferedImage scaleToFit(BufferedImage image, int maxWidth, int maxHeight) {
        if (image.getWidth() <= maxWidth && image.getHeight() <= maxHeight) {
            return image;
        }

        double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    // 加载图像文件
    public static Mat loadImage(String path) {
        Mat image = Imgcodecs.imread(path, Imgcodecs.IMREAD_COLOR);
        if (image.empty()) {
            throw new RuntimeException("无法加载图像: " + path);
        }
        return image;
    }

    /**
     * Loads a map from classpath resources, or from a filesystem path relative to user.dir / absolute.
     */
    public static Mat loadMapImage(String mapPath) {
        if (mapPath == null || mapPath.isBlank()) {
            throw new IllegalArgumentException("map path is required");
        }
        try {
            return loadResourceImage(mapPath);
        } catch (RuntimeException classpathFailure) {
            Path relative = Paths.get(mapPath);
            if (Files.isRegularFile(relative)) {
                return loadImage(relative.toAbsolutePath().toString());
            }
            Path fromUserDir = Paths.get(System.getProperty("user.dir", ".")).resolve(mapPath);
            if (Files.isRegularFile(fromUserDir)) {
                return loadImage(fromUserDir.toAbsolutePath().toString());
            }
            throw new RuntimeException(
                    "无法加载地图: " + mapPath + "（非 classpath 资源，且工作目录下不存在该文件）",
                    classpathFailure
            );
        }
    }

    public static Mat loadResourceImage(String resourcePath) {
        try {
            Path path = materializeResource(resourcePath);
            return loadImage(path.toString());
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            throw new RuntimeException("无法解析图像资源路径: " + resourcePath, e);
        }
    }

    public static BufferedImage loadResourceBufferedImage(String resourcePath) {
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new RuntimeException("无法加载图像资源: " + resourcePath);
            }
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new RuntimeException("无法解析图像资源: " + resourcePath);
            }
            return image;
        } catch (IOException e) {
            throw new RuntimeException("无法读取图像资源: " + resourcePath, e);
        }
    }

    // 截取指定区域
    public static Mat extractRegion(Mat src, Rect roi) {
        return new Mat(src, clampRect(src, roi));
    }

    // 截取中心区域
    public static Mat extractCenterArea(Mat src, Point center, int size) {
        Rect roi = new Rect((int) (center.x - size / 2), (int) (center.y - size / 2), size, size);
        return new Mat(src, clampRect(src, roi));
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

    private static Rect clampRect(Mat src, Rect roi) {
        int x = Math.max(0, Math.min(roi.x, src.cols() - 1));
        int y = Math.max(0, Math.min(roi.y, src.rows() - 1));
        int width = Math.max(1, Math.min(roi.width, src.cols() - x));
        int height = Math.max(1, Math.min(roi.height, src.rows() - y));
        return new Rect(x, y, width, height);
    }

    private static Path materializeResource(String resourcePath) throws IOException, URISyntaxException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        if (resource == null) {
            throw new IOException("无法定位资源: " + resourcePath);
        }
        if ("file".equalsIgnoreCase(resource.getProtocol())) {
            return Paths.get(resource.toURI());
        }

        String extension = "";
        int dotIndex = resourcePath.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = resourcePath.substring(dotIndex);
        }

        Path tempFile = Files.createTempFile("auto-action-resource-", extension);
        tempFile.toFile().deleteOnExit();
        try (InputStream inputStream = resource.openStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }
}
