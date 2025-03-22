package com.auto.opencv;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.net.URL;

public class BinarizationGUI {
    JPanel mainPanel;
    private Mat originalImage;
    private Mat processedImage;
    private JTabbedPane tabbedPane;
    private JLabel statusLabel;
    private JSlider thresholdSlider = new JSlider(0, 255, 127);
    private JCheckBox binaryCheckBox = new JCheckBox();
    private JFrame imageViewerFrame;
    private JLabel imageLabel;
    public BinarizationGUI() {
        JFrame frame = new JFrame("图像处理工具 v2.0");
        frame.setSize(500, 300); // 调整主窗口大小
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        tabbedPane = new JTabbedPane();
        frame.add(tabbedPane, BorderLayout.CENTER);

        JPanel imageProcessingTab = createImageProcessingTab();
        tabbedPane.addTab("图像处理", imageProcessingTab);

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14)); // 设置状态栏字体
        frame.add(statusLabel, BorderLayout.SOUTH);
        // 设置窗口居中
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

    }

    private JPanel createImageProcessingTab() {
        mainPanel = new JPanel(new BorderLayout());

        // 使用 GridBagLayout 布局
        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 设置组件间距
        gbc.fill = GridBagConstraints.HORIZONTAL; // 组件水平填充

        // 添加按钮
        addButton(controlPanel, gbc, "截图载入", e -> captureScreenshot(), 0, 0);
        addButton(controlPanel, gbc, "本地载入", e -> loadImage(), 1, 0);
        addLabel(controlPanel, gbc, "二值化阈值:", 0, 1);
        addSlider(controlPanel,thresholdSlider,false,e ->  processImage(), gbc, 1, 1);
        addLabel(controlPanel, gbc, "二值化:", 0, 2);
        addCheckBox(controlPanel,binaryCheckBox, gbc, "启用二值化", e -> {
            processImage(); // 复选框状态改变时，调用 processImage 方法
             // 启用或禁用滑块
            thresholdSlider.setEnabled(binaryCheckBox.isSelected());
        }, 1, 2);
        // 添加保留灰色区域的按钮
        addButton(controlPanel, gbc, "保留灰色区域", e -> preserveGrayArea(), 0, 3);
        addButton(controlPanel, gbc, "保存图片", e -> saveImage(), 0, 3);
        addButton(controlPanel, gbc, "图像预览", e -> openHighGuiWindow(), 1, 3);

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        return mainPanel;
    }

    // 添加按钮的辅助方法
    private void addButton(JPanel panel, GridBagConstraints gbc, String text, java.awt.event.ActionListener listener, int x, int y) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        button.setBackground(new Color(70, 130, 180)); // 设置背景色
        button.setForeground(Color.WHITE); // 设置文字颜色
        button.setFont(new Font("微软雅黑", Font.BOLD, 14)); // 设置字体
        button.setFocusPainted(false); // 去除焦点边框
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20)); // 设置边距
        gbc.gridx = x;
        gbc.gridy = y;
        panel.add(button, gbc);
    }

    // 添加标签的辅助方法
    private void addLabel(JPanel panel, GridBagConstraints gbc, String text, int x, int y) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("微软雅黑", Font.PLAIN, 14)); // 设置字体
        gbc.gridx = x;
        gbc.gridy = y;
        panel.add(label, gbc);
    }

    // 添加滑块的辅助方法
    private void addSlider(JPanel panel, JSlider slider, boolean isEnabled, ChangeListener listener, GridBagConstraints gbc, int x, int y) {
        slider.addChangeListener(listener);
        slider.setEnabled(isEnabled);
        gbc.gridx = x;
        gbc.gridy = y;
        panel.add(slider, gbc);
    }

// 添加复选框的辅助方法
    private void addCheckBox(JPanel panel,JCheckBox checkBox, GridBagConstraints gbc, String text, ActionListener listener, int x, int y) {
       checkBox.setText(text);
        checkBox.setFont(new Font("微软雅黑", Font.PLAIN, 14)); // 设置字体
        checkBox.addActionListener(listener); // 绑定监听器
        gbc.gridx = x;
        gbc.gridy = y;
        panel.add(checkBox, gbc);
    }

    private void processImage() {
        if (originalImage == null) return;
        try {
            processedImage = new Mat(originalImage.rows(), originalImage.cols(), CvType.CV_8UC1);

            if (binaryCheckBox.isSelected()) {
                Mat gray = new Mat();
                Imgproc.cvtColor(originalImage, gray, Imgproc.COLOR_BGR2GRAY); // 转换为灰度图
                Imgproc.threshold(gray, processedImage, thresholdSlider.getValue(), 255, Imgproc.THRESH_BINARY); // 二值化处理
            } else {
                // 如果不启用二值化，直接复制原图
                originalImage.copyTo(processedImage);
            }

            updateStatus(binaryCheckBox.isSelected() ?
                    "二值化已启用 - 当前阈值: " + thresholdSlider.getValue() :
                    "显示原始图像");

            // 更新 HighGui 窗口中的图片
            updateHighGuiWindow();
        } catch (Exception e) {
            updateStatus("处理错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void preserveGrayArea() {
        if (originalImage == null) return;

        try {
            // 转换为HSV颜色空间
            Mat hsvImage = new Mat();
            Imgproc.cvtColor(originalImage, hsvImage, Imgproc.COLOR_BGR2HSV);

            // 定义橙色的HSV范围
            Scalar lowerOrange = new Scalar(10, 100, 100); // 橙色下限
            Scalar upperOrange = new Scalar(25, 255, 255); // 橙色上限

            // 创建掩码，标记橙色区域
            Mat orangeMask = new Mat();
            Core.inRange(hsvImage, lowerOrange, upperOrange, orangeMask);

            // 反转掩码，保留非橙色区域
            Mat invertedMask = new Mat();
            Core.bitwise_not(orangeMask, invertedMask);

            // 应用掩码，去除橙色
            processedImage = new Mat();
            Core.bitwise_and(originalImage, originalImage, processedImage, invertedMask);

            updateStatus("已去除橙色箭头");

            // 更新 HighGui 窗口中的图片
            updateHighGuiWindow();
        } catch (Exception e) {
            updateStatus("处理错误: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private void openHighGuiWindow() {
        if (originalImage == null) {
            updateStatus("请先载入或截图一张图片");
            return;
        }

        if (imageViewerFrame == null || !imageViewerFrame.isVisible()) {
            // 如果窗口未打开，则创建并显示窗口
            updateHighGuiWindow();
        } else {
            // 如果窗口已打开，则关闭窗口
            imageViewerFrame.dispose();
            imageViewerFrame = null; // 释放窗口资源
        }
    }


    // 修改 updateHighGuiWindow 方法
    private void updateHighGuiWindow() {
        if (imageViewerFrame == null) {
            // 创建图片展示窗口
            imageViewerFrame = new JFrame("Image Viewer");
            imageViewerFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            imageViewerFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent windowEvent) {
                    imageViewerFrame = null; // 窗口关闭时，释放资源
                }
            });

            imageLabel = new JLabel();
            imageViewerFrame.add(new JScrollPane(imageLabel), BorderLayout.CENTER);
            imageViewerFrame.setVisible(true);
        }

        // 更新图片
        BufferedImage imageToShow = null;
        if (processedImage != null) {
            imageToShow = matToBufferedImage(processedImage);
        } else if (originalImage != null) {
            imageToShow = matToBufferedImage(originalImage);
        }

        if (imageToShow != null) {
            ImageIcon icon = new ImageIcon(imageToShow);
            imageLabel.setIcon(icon);

            // 根据图片大小动态调整窗口尺寸
            int imageWidth = imageToShow.getWidth() + 50;
            int imageHeight = imageToShow.getHeight() + 50;
            int maxWidth = Toolkit.getDefaultToolkit().getScreenSize().width - 100; // 限制最大宽度
            int maxHeight = Toolkit.getDefaultToolkit().getScreenSize().height - 100; // 限制最大高度

            int windowWidth = Math.min(imageWidth, maxWidth);
            int windowHeight = Math.min(imageHeight, maxHeight);

            imageViewerFrame.setSize(windowWidth, windowHeight);
        }

        // 调整窗口位置
        JFrame mainFrame = (JFrame) SwingUtilities.getWindowAncestor(mainPanel);
        if (mainFrame != null) {
            int padding = 0; // 主窗口与图片窗口之间的间距
            int x = mainFrame.getX() + mainFrame.getWidth() + padding;
            int y = mainFrame.getY();
            imageViewerFrame.setLocation(x, y);
        }
    }






    private BufferedImage matToBufferedImage(Mat mat) {
        int type = mat.channels() == 1 ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, data);
        return image;
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void captureScreenshot() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screenshot = robot.createScreenCapture(screenRect);
            originalImage = bufferedImageToMat(screenshot);
            processImage();
            updateStatus("截图载入成功");
        } catch (AWTException ex) {
            updateStatus("截图失败: " + ex.getMessage());
        }
    }

    private Mat bufferedImageToMat(BufferedImage image) {
        BufferedImage convertedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        convertedImage.getGraphics().drawImage(image, 0, 0, null);
        Mat mat = new Mat(convertedImage.getHeight(), convertedImage.getWidth(), CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) convertedImage.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        return mat;
    }

    private void loadImage() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(null);

        if (result == JFileChooser.APPROVE_OPTION) {
            new Thread(() -> {
                try {
                    String path = fileChooser.getSelectedFile().getAbsolutePath();
                    Mat tempImage = Imgcodecs.imread(path, Imgcodecs.IMREAD_COLOR);

                    if (tempImage == null || tempImage.empty()) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(null, "无法加载图片\n可能原因：\n1.文件路径包含中文\n2.格式不支持\n3.文件已损坏");
                            updateStatus("加载失败: " + path);
                        });
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        originalImage = tempImage;
                        processImage();
                        updateStatus("成功加载: " + path);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "加载错误: " + ex.getMessage());
                        updateStatus("加载失败: " + ex.getMessage());
                    });
                }
            }).start();
        } else {
            updateStatus("已取消选择文件");
        }
    }

    private void saveImage() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            Imgcodecs.imwrite(fileChooser.getSelectedFile().getPath(), processedImage);
            updateStatus("图片保存成功: " + fileChooser.getSelectedFile().getName());
        }
    }

    public static void main(String[] args) {
        URL url = ClassLoader.getSystemResource("lib/opencv/opencv_java4110.dll");
        System.load(url.getPath());

        SwingUtilities.invokeLater(() -> {
            new BinarizationGUI();
        });
    }
}
