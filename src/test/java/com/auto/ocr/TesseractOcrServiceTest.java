package com.auto.ocr;

import com.auto.config.OcrConfig;
import com.auto.config.OcrRegionConfig;
import com.auto.config.OcrRegionSource;
import com.auto.config.RegionConfig;
import com.auto.vision.OpenCvLoader;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TesseractOcrServiceTest {
    @BeforeClass
    public static void loadOpenCv() {
        OpenCvLoader.load();
    }

    @Test
    public void recognizeDigitsInsideConfiguredRegion() {
        BufferedImage image = new BufferedImage(220, 80, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 34));
        graphics.drawString("12345", 20, 52);
        graphics.dispose();

        OcrRegionConfig region = new OcrRegionConfig(
                "coords",
                OcrRegionSource.WINDOW,
                new RegionConfig(0, 0, image.getWidth(), image.getHeight()),
                3.0,
                170,
                "0123456789"
        );
        OcrConfig config = new OcrConfig(true, "eng", 7, "0123456789", List.of(region));

        List<OcrResult> results = new TesseractOcrService().recognize(config, image, null);

        assertEquals(1, results.size());
        OcrResult result = results.get(0);
        assertEquals("coords", result.name());
        assertEquals("12345", result.text().replaceAll("\\D", ""));
        assertEquals(new Rectangle(0, 0, 220, 80), result.bounds());
        assertNotNull(result.preprocessedPreviewImage());
    }

    @Test
    public void recognizeClampsConfiguredBoundsToImageArea() {
        BufferedImage image = new BufferedImage(80, 40, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        OcrRegionConfig region = new OcrRegionConfig(
                "edge",
                OcrRegionSource.WINDOW,
                new RegionConfig(120, 50, 30, 20),
                2.0,
                170,
                "0123456789"
        );
        OcrConfig config = new OcrConfig(true, "eng", 7, "0123456789", List.of(region));

        List<OcrResult> results = new TesseractOcrService().recognize(config, image, null);

        assertEquals(1, results.size());
        assertEquals(new Rectangle(79, 39, 1, 1), results.get(0).bounds());
        assertNotNull(results.get(0).preprocessedPreviewImage());
    }
}
