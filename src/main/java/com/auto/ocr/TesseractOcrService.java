package com.auto.ocr;

import com.auto.config.OcrConfig;
import com.auto.config.OcrRegionConfig;
import com.auto.config.OcrRegionSource;
import com.auto.opencv.utils.ImageProcessor;
import com.auto.util.ResourceFileSupport;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class TesseractOcrService implements OcrService {
    private static final AtomicReference<Path> TESSDATA_ROOT = new AtomicReference<>();

    @Override
    public List<OcrResult> recognize(OcrConfig config, BufferedImage windowImage, BufferedImage miniMapImage) {
        if (!config.enabled()) {
            return List.of();
        }

        ITesseract tesseract = createTesseract(config);
        List<OcrResult> results = new ArrayList<>();
        for (OcrRegionConfig regionConfig : config.regions()) {
            BufferedImage source = regionConfig.source() == OcrRegionSource.MINIMAP ? miniMapImage : windowImage;
            if (source == null) {
                continue;
            }
            applyWhitelist(tesseract, config.defaultWhitelist(), regionConfig.whitelist());
            PreparedRegion prepared = preprocess(source, regionConfig);
            try {
                String text = tesseract.doOCR(prepared.previewImage()).trim();
                List<Word> words = tesseract.getWords(prepared.previewImage(), ITessAPI.TessPageIteratorLevel.RIL_WORD);
                double confidence = words.isEmpty()
                        ? 0.0
                        : words.stream().mapToDouble(Word::getConfidence).average().orElse(0.0);
                results.add(new OcrResult(regionConfig.name(), text, confidence, prepared.bounds(), prepared.previewImage()));
            } catch (TesseractException e) {
                throw new IllegalStateException("OCR failed for region " + regionConfig.name(), e);
            }
        }
        return results;
    }

    private static void applyWhitelist(ITesseract tesseract, String defaultWhitelist, String regionWhitelist) {
        String whitelist = regionWhitelist == null || regionWhitelist.isBlank() ? defaultWhitelist : regionWhitelist;
        tesseract.setTessVariable("tessedit_char_whitelist", whitelist == null ? "" : whitelist);
    }

    private static ITesseract createTesseract(OcrConfig config) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(resolveTessdataRoot().toString());
        tesseract.setLanguage(config.language());
        tesseract.setPageSegMode(config.pageSegMode());
        return tesseract;
    }

    private static PreparedRegion preprocess(BufferedImage source, OcrRegionConfig config) {
        Mat sourceMat = ImageProcessor.bufferedImageToMat(source);
        Rectangle bounds = clampBounds(source, config.region());
        Rect roi = new Rect(bounds.x, bounds.y, bounds.width, bounds.height);
        Mat cropped = ImageProcessor.extractRegion(sourceMat, roi).clone();
        Mat gray = new Mat();
        Imgproc.cvtColor(cropped, gray, Imgproc.COLOR_BGR2GRAY);
        Mat thresholded = new Mat();
        if (config.threshold() != null) {
            Imgproc.threshold(gray, thresholded, config.threshold(), 255, Imgproc.THRESH_BINARY);
        } else {
            Imgproc.threshold(gray, thresholded, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        }
        Mat scaled = new Mat();
        Imgproc.resize(
                thresholded,
                scaled,
                new Size(
                        Math.max(1, Math.round((float) (thresholded.cols() * config.scale()))),
                        Math.max(1, Math.round((float) (thresholded.rows() * config.scale())))
                ),
                0,
                0,
                Imgproc.INTER_CUBIC
        );
        return new PreparedRegion(bounds, ImageProcessor.matToBufferedImage(scaled));
    }

    private static Rectangle clampBounds(BufferedImage source, com.auto.config.RegionConfig region) {
        int x = Math.max(0, Math.min(region.x(), source.getWidth() - 1));
        int y = Math.max(0, Math.min(region.y(), source.getHeight() - 1));
        int width = Math.max(1, Math.min(region.width(), source.getWidth() - x));
        int height = Math.max(1, Math.min(region.height(), source.getHeight() - y));
        return new Rectangle(
                x,
                y,
                width,
                height
        );
    }

    private static Path resolveTessdataRoot() {
        Path existing = TESSDATA_ROOT.get();
        if (existing != null) {
            return existing;
        }
        Path root = ResourceFileSupport.materializeDirectory(
                "auto-action-tessdata-",
                Map.of("tessdata/eng.traineddata", "tessdata/eng.traineddata")
        );
        TESSDATA_ROOT.compareAndSet(null, root.resolve("tessdata"));
        return TESSDATA_ROOT.get();
    }

    private record PreparedRegion(Rectangle bounds, BufferedImage previewImage) {
    }
}
