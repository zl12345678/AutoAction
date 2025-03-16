// 新增文件：PathPlanningGUI.java
package org.example.opencv;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.net.URL;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class PathPlanningGUI extends JFrame {
    private Mat originalImage;
    private Point startPoint, endPoint;
    private JLabel imageLabel;
    private JButton btnLoadImage, btnRun,btnApply,btnLoadTemplate;
    private JLabel statusLabel;
    private Mat displayMat; // 用于叠加显示所有标记
    // 在PathPlanningGUI类中添加成员变量
    private Mat templateImage;
    public PathPlanningGUI() {
        initUI();
        setupOpenCV();
    }
    // 添加模板匹配方法
    private Point matchTemplate(Mat source, Mat template) {
        ImageValidator.validateForTemplateMatching(source, template);
        Mat result = new Mat();
        Imgproc.matchTemplate(source, template, result, Imgproc.TM_CCOEFF_NORMED);

        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
        Point matchLoc = mmr.maxLoc;

        // 返回匹配区域中心点
        return new Point(
                matchLoc.x + template.cols()/2.0,
                matchLoc.y + template.rows()/2.0
        );
    }
    private void loadTemplate() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() {
                    try {
                        // 使用二值化工具处理
                        ImageBinarizer binarizer = new ImageBinarizer();
                        String path = fileChooser.getSelectedFile().getPath();
                        // 加载原始图像
                        Mat rawImage = Imgcodecs.imread(path);
                        templateImage = binarizer.binarize(rawImage);
                        findStartPointByTemplate();
                    } catch (Exception e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(null, "模板加载失败");
                    }
                    return null;
                }
            }.execute();
        }
    }
    public class ImageValidator {
        public static void validateForTemplateMatching(Mat source, Mat template) {
            // 检查通道数
            if (source.channels() != template.channels()) {
                throw new IllegalArgumentException(
                        String.format("通道数不匹配: 源图%d通道 vs 模板%d通道",
                                source.channels(), template.channels())
                );
            }

            // 检查数据类型
            if (source.depth() != CvType.CV_8U || template.depth() != CvType.CV_8U) {
                throw new IllegalArgumentException("只支持CV_8U类型图像");
            }
        }
    }
    private void findStartPointByTemplate() {
        if (originalImage == null || templateImage == null) {
            JOptionPane.showMessageDialog(this, "请先加载地图和模板");
            return;
        }

        // 转换为灰度图提高匹配速度
        Mat srcGray = new Mat();
        Mat tmpGray = new Mat();
//        Imgproc.cvtColor(originalImage, srcGray, Imgproc.COLOR_BGR2GRAY);
//        Imgproc.cvtColor(templateImage, tmpGray, Imgproc.COLOR_BGR2GRAY);
        // 动态转换源图像
        if (originalImage.channels() == 3) {
            Imgproc.cvtColor(originalImage, srcGray, Imgproc.COLOR_BGR2GRAY);
        } else {
            originalImage.copyTo(srcGray);
        }

        // 动态转换模板图像
        if (templateImage.channels() == 3) {
            Imgproc.cvtColor(templateImage, tmpGray, Imgproc.COLOR_BGR2GRAY);
        } else {
            templateImage.copyTo(tmpGray);
        }

        startPoint = matchTemplate(srcGray, tmpGray);
        drawMatchArea(startPoint, tmpGray.size()); // 绘制匹配区域
        redrawMarkers();

        srcGray.release();
        tmpGray.release();
    }
    private void drawMatchArea(Point center, Size templateSize) {
        if (displayMat == null) return;

        // 绘制匹配矩形
        Point topLeft = new Point(
                center.x - templateSize.width/2,
                center.y - templateSize.height/2
        );
        Scalar green = new Scalar(0, 255, 0);
        Imgproc.rectangle(displayMat,
                topLeft,
                new Point(topLeft.x + templateSize.width,
                        topLeft.y + templateSize.height),
                green, 2);

        // 绘制中心点
        Imgproc.circle(displayMat, center, 8, green, -1);
        updateImageDisplay(displayMat);
    }
    private void loadAndBinarizeImage() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() {
                    try {
                        String path = fileChooser.getSelectedFile().getPath();

                        // 加载原始图像
                        Mat rawImage = Imgcodecs.imread(path);

                        // 使用二值化工具处理
                        ImageBinarizer binarizer = new ImageBinarizer();
                        originalImage = binarizer.binarize(rawImage);

                        // 释放资源
                        rawImage.release();

                        // 更新显示
                        SwingUtilities.invokeLater(() -> {
                            updateImageDisplay(originalImage);
                            autoSetPoints();
                            statusLabel.setText("已二值化图像: " + path);
                        });
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(null, "二值化失败: " + e.getMessage())
                        );
                    }
                    return null;
                }
            }.execute();
        }
    }

    private void initUI() {
        setTitle("A*路径规划工具");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // 主容器
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // 工具栏
        JPanel toolbar = new JPanel();
         btnLoadTemplate = new JButton("加载模板");
        btnLoadImage = new JButton("加载地图");
        btnRun = new JButton("开始规划");
        statusLabel = new JLabel("就绪");
        JButton btnBinarize = new JButton("加载并二值化");
        btnBinarize.addActionListener(e -> loadAndBinarizeImage());
        btnLoadImage.addActionListener(e -> loadImage());
        btnRun.addActionListener(e -> runPathPlanning());

        // 可添加文本框配置起终点（在toolbar面板添加）
        JTextField txtStart = new JTextField("140,480", 5);
        JTextField txtEnd = new JTextField("120,50", 8);
        btnApply = new JButton("应用坐标");

        btnApply.addActionListener(e -> {
            try {
                String[] start = txtStart.getText().split(",");
                String[] end = txtEnd.getText().split(",");

                startPoint = new Point(
                        Integer.parseInt(start[0].trim()),
                        Integer.parseInt(start[1].trim())
                );

                endPoint = new Point(
                        Integer.parseInt(end[0].trim()),
                        Integer.parseInt(end[1].trim())
                );

                redrawMarkers();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "坐标格式错误");
            }
        });
        btnLoadTemplate.addActionListener(e -> loadTemplate());
// 将以下组件添加到toolbar
        toolbar.add(new JLabel("起点:"));
        toolbar.add(txtStart);
        toolbar.add(new JLabel("终点:"));
        toolbar.add(txtEnd);
        toolbar.add(btnApply);


        toolbar.add(btnLoadImage);
        toolbar.add(btnLoadTemplate);
        toolbar.add(btnRun);
        toolbar.add(statusLabel);
        toolbar.add(btnBinarize);
        // 图像显示区域
        imageLabel = new JLabel() {
            @Override
            public Dimension getPreferredSize() {
                if (getIcon() == null) {
                    return new Dimension(800, 600);
                }
                // 根据图像比例计算最佳尺寸
                Icon icon = getIcon();
                int w = icon.getIconWidth();
                int h = icon.getIconHeight();
                return new Dimension(w, h);
            }
        };
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        mainPanel.add(toolbar, BorderLayout.NORTH);
        mainPanel.add(imageLabel, BorderLayout.CENTER); // 直接添加imageLabel
        // 添加窗口缩放监听
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateImageDisplay(displayMat != null ? displayMat : originalImage);
            }
        });

        add(mainPanel);
    }

    private void setupOpenCV() {
        // OpenCV初始化代码（与原有main方法相同）
        URL url = ClassLoader.getSystemResource("lib/opencv/opencv_java4110.dll");
        System.load(url.getPath());
    }

    private void loadImage() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() {
                    try {
                        String path = fileChooser.getSelectedFile().getPath();
                        originalImage = Imgcodecs.imread(path);
                        displayMat = null; // 重置显示缓存
                        updateImageDisplay(originalImage);
                        autoSetPoints(); // 新增自动设置点
                        statusLabel.setText("已加载图像: " + path);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(null, "图像加载失败");
                    }
                    return null;
                }
            }.execute();
        }
    }
    private double getImageScale() {
        // 获取当前显示图像的尺寸
        Icon icon = imageLabel.getIcon();
        if (icon == null || originalImage == null) {
            return 1.0; // 默认不缩放
        }

        // 获取显示尺寸和原始尺寸
        int displayWidth = icon.getIconWidth();
        int displayHeight = icon.getIconHeight();
        int originalWidth = originalImage.cols();
        int originalHeight = originalImage.rows();

        // 计算宽高缩放比例（取较小值保持比例）
        double widthScale = (double) displayWidth / originalWidth;
        double heightScale = (double) displayHeight / originalHeight;

        return Math.min(widthScale, heightScale);
    }

    // 在PathPlanningGUI类中添加：
    private void autoSetPoints() {
        if (originalImage == null) return;

        // 自动设置起点为左上角，终点为右下角
//        startPoint = new Point(0, 0);
        endPoint = new Point(120, 50);
        redrawMarkers();
    }
    // 新增统一标记重绘方法
    private void redrawMarkers() {
        if (originalImage == null) return;

        // 1. 重置显示Mat为原始图像
        displayMat = originalImage.clone();

        // 2. 绘制当前有效标记
        Scalar red = new Scalar(0, 0, 255);   // BGR格式：红色
        Scalar blue = new Scalar(255, 0, 0);  // BGR格式：蓝色

        if (startPoint != null) {
            Imgproc.circle(displayMat, startPoint, 5, red, -1);
        }
        if (endPoint != null) {
            Imgproc.circle(displayMat, endPoint, 5, blue, -1);
        }

        // 3. 更新显示
        updateImageDisplay(displayMat);
    }
    // 新增自动设置终点方法

    private void runPathPlanning() {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "请先加载地图");
            return;
        }
        if (startPoint == null) {
            JOptionPane.showMessageDialog(this, "请先通过模板匹配设置起点");
            return;
        }
        if (endPoint == null) {
            autoSetPoints(); // 自动设置终点到右下角
        }

        // 自动验证起终点有效性
        if (startPoint == null || endPoint == null) {
            autoSetPoints();
        }
        new SwingWorker<int[][], Void>() {
            protected int[][] doInBackground() {
                AStarPathPlanner planner = new AStarPathPlanner(
                    originalImage, 
                    startPoint,
                    endPoint,
                    200.0
                );
                return planner.findPath();
            }
            
            protected void done() {
                try {
                    int[][] path = get();
                    drawPathOnImage(path);
                    statusLabel.setText("路径规划完成");
                } catch (Exception e) {
                    statusLabel.setText("路径规划失败: " + e.getMessage());
                }
            }
        }.execute();
    }

    // 图像显示相关辅助方法
    private void updateImageDisplay(Mat mat) {
        if (mat == null || mat.empty()) return;

        BufferedImage image = matToBufferedImage(mat);

        // 根据当前窗口尺寸计算缩放比例
        int maxWidth = getContentPane().getWidth() - 20; // 留出边距
        int maxHeight = getContentPane().getHeight() - 50; // 减去工具栏高度

        // 保持宽高比缩放
        double ratio = Math.min(
                (double)maxWidth / image.getWidth(),
                (double)maxHeight / image.getHeight()
        );

        Image scaledImage = image.getScaledInstance(
                (int)(image.getWidth() * ratio),
                (int)(image.getHeight() * ratio),
                Image.SCALE_SMOOTH
        );

        imageLabel.setIcon(new ImageIcon(scaledImage));
        revalidate();
        repaint();
    }

    private void drawPathOnImage(int[][] path) {
        if (displayMat == null) return;

        Mat pathMat = displayMat.clone(); // 在现有标记基础上绘制路径
        for (int[] point : path) {
            Imgproc.circle(pathMat,
                    new Point(point[0], point[1]),
                    2, new Scalar(0, 255, 0), -1);
        }
        updateImageDisplay(pathMat);
        pathMat.release();
    }

    private static BufferedImage matToBufferedImage(Mat mat) {
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("Invalid Mat object");
        }
        // 单通道图像处理
        if (mat.channels() == 1) {
            BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), BufferedImage.TYPE_BYTE_GRAY);
            WritableRaster raster = image.getRaster();

            byte[] data = new byte[mat.cols() * mat.rows()];
            mat.get(0, 0, data);

            // 反转灰度值：使道路显示为白色（255），障碍为黑色（0）
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (255 - (data[i] & 0xFF));
            }

            raster.setDataElements(0, 0, mat.cols(), mat.rows(), data);
            return image;
        }
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }

        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        WritableRaster raster = image.getRaster();
        DataBufferByte dataBuffer = (DataBufferByte) raster.getDataBuffer();
        byte[] data = dataBuffer.getData();

        mat.get(0, 0, data);  // 直接从Mat拷贝数据到BufferedImage

        // 如果原始Mat是BGR格式，需要转换为RGB
        if (mat.channels() == 3) {
            for (int i = 0; i < data.length; i += 3) {
                byte temp = data[i];       // B
                data[i] = data[i + 2];     // R
                data[i + 2] = temp;        // B -> R
            }
        }

        return image;
    }

    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PathPlanningGUI().setVisible(true);
        });
    }
}
