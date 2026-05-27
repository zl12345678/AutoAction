package com.auto.app;

import com.auto.opencv.utils.ImageProcessor;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;

final class ImagePreviewPanel extends JPanel {
    private final JLabel titleLabel = new JLabel("暂无图像");
    private final JLabel imageLabel = new JLabel("暂无图像", SwingConstants.CENTER);
    private BufferedImage originalImage;

    ImagePreviewPanel() {
        super(new BorderLayout(8, 8));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        imageLabel.setVerticalAlignment(SwingConstants.TOP);
        add(titleLabel, BorderLayout.NORTH);
        add(new JScrollPane(imageLabel), BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    void setImage(BufferedImage image, String title) {
        originalImage = image;
        titleLabel.setText(title == null || title.isBlank() ? "暂无图像" : title);
        if (image == null) {
            imageLabel.setIcon(null);
            imageLabel.setText("暂无图像");
            return;
        }

        imageLabel.setText(null);
        imageLabel.setIcon(new ImageIcon(ImageProcessor.scaleToFit(image, 1100, 720)));
    }

    BufferedImage originalImage() {
        return originalImage;
    }
}
