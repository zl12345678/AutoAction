package com.auto.detection;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.auto.config.RegionConfig;
import com.auto.config.YoloConfig;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OnnxYoloDetectionService implements ObjectDetectionService {
    private final YoloInferenceEngine inferenceEngine;

    public OnnxYoloDetectionService() {
        this(new OnnxRuntimeInferenceEngine());
    }

    OnnxYoloDetectionService(YoloInferenceEngine inferenceEngine) {
        this.inferenceEngine = inferenceEngine;
    }

    @Override
    public List<DetectedObject> detect(YoloConfig config, BufferedImage sourceImage) {
        if (!config.enabled()) {
            return List.of();
        }
        if (sourceImage == null) {
            return List.of();
        }
        if (config.modelPath().isBlank()) {
            throw new IllegalArgumentException("YOLO modelPath is required when detection is enabled");
        }

        Rectangle cropBounds = clampRegion(config.region(), sourceImage.getWidth(), sourceImage.getHeight());
        BufferedImage cropped = sourceImage.getSubimage(cropBounds.x, cropBounds.y, cropBounds.width, cropBounds.height);
        LetterboxResult input = letterbox(cropped, config.inputWidth(), config.inputHeight());
        RawYoloResult raw = inferenceEngine.run(config, input.tensorInput(), input.width(), input.height());

        List<DetectedObject> detections = new ArrayList<>();
        for (float[] prediction : raw.predictions()) {
            Candidate candidate = toCandidate(prediction, raw.labels(), config.confidenceThreshold());
            if (candidate == null) {
                continue;
            }
            Rectangle bounds = mapToOriginalBounds(candidate, input, cropBounds, cropped.getWidth(), cropped.getHeight());
            if (bounds.width <= 0 || bounds.height <= 0) {
                continue;
            }
            String label = raw.labels().getOrDefault(candidate.classId(), "class-" + candidate.classId());
            if (!config.classesOfInterest().isEmpty() && !config.classesOfInterest().contains(label)) {
                continue;
            }
            detections.add(new DetectedObject(label, candidate.score(), bounds, candidate.classId()));
        }

        return nonMaximumSuppression(detections, config.iouThreshold(), config.maxDetections());
    }

    static LetterboxResult letterbox(BufferedImage source, int targetWidth, int targetHeight) {
        double scale = Math.min((double) targetWidth / source.getWidth(), (double) targetHeight / source.getHeight());
        int resizedWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int resizedHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int padX = (targetWidth - resizedWidth) / 2;
        int padY = (targetHeight - resizedHeight) / 2;

        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, targetWidth, targetHeight);
        graphics.drawImage(source, padX, padY, resizedWidth, resizedHeight, null);
        graphics.dispose();

        float[] tensor = new float[targetWidth * targetHeight * 3];
        int index = 0;
        for (int channel = 0; channel < 3; channel++) {
            for (int y = 0; y < targetHeight; y++) {
                for (int x = 0; x < targetWidth; x++) {
                    int rgb = canvas.getRGB(x, y);
                    int value = switch (channel) {
                        case 0 -> (rgb >> 16) & 0xFF;
                        case 1 -> (rgb >> 8) & 0xFF;
                        default -> rgb & 0xFF;
                    };
                    tensor[index++] = value / 255.0f;
                }
            }
        }
        return new LetterboxResult(tensor, targetWidth, targetHeight, scale, padX, padY);
    }

    static List<DetectedObject> nonMaximumSuppression(List<DetectedObject> detections, double iouThreshold, int maxDetections) {
        Map<Integer, List<DetectedObject>> grouped = new LinkedHashMap<>();
        for (DetectedObject detection : detections) {
            grouped.computeIfAbsent(detection.classId(), ignored -> new ArrayList<>()).add(detection);
        }
        List<DetectedObject> kept = new ArrayList<>();
        for (List<DetectedObject> group : grouped.values()) {
            group.sort(Comparator.comparingDouble(DetectedObject::score).reversed());
            while (!group.isEmpty() && kept.size() < maxDetections) {
                DetectedObject best = group.remove(0);
                kept.add(best);
                group.removeIf(candidate -> intersectionOverUnion(best.bounds(), candidate.bounds()) >= iouThreshold);
            }
        }
        return kept;
    }

    static double intersectionOverUnion(Rectangle a, Rectangle b) {
        int x1 = Math.max(a.x, b.x);
        int y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);
        int intersectionWidth = Math.max(0, x2 - x1);
        int intersectionHeight = Math.max(0, y2 - y1);
        double intersection = intersectionWidth * intersectionHeight;
        double union = (a.width * a.height) + (b.width * b.height) - intersection;
        return union <= 0.0 ? 0.0 : intersection / union;
    }

    static Candidate toCandidate(float[] prediction, Map<Integer, String> labels, double confidenceThreshold) {
        if (prediction.length < 5) {
            return null;
        }
        boolean hasObjectness = labels.isEmpty() ? prediction.length > 5 : prediction.length == labels.size() + 5;
        int classStart = hasObjectness ? 5 : 4;
        double objectness = hasObjectness ? prediction[4] : 1.0;
        double bestClassScore = -1.0;
        int bestClassId = -1;
        for (int index = classStart; index < prediction.length; index++) {
            if (prediction[index] > bestClassScore) {
                bestClassScore = prediction[index];
                bestClassId = index - classStart;
            }
        }
        double score = objectness * Math.max(0.0, bestClassScore);
        if (bestClassId < 0 || score < confidenceThreshold) {
            return null;
        }
        return new Candidate(prediction[0], prediction[1], prediction[2], prediction[3], bestClassId, score);
    }

    static Rectangle mapToOriginalBounds(
            Candidate candidate,
            LetterboxResult input,
            Rectangle cropBounds,
            int cropWidth,
            int cropHeight
    ) {
        double x = (candidate.centerX() - candidate.width() / 2.0 - input.padX()) / input.scale();
        double y = (candidate.centerY() - candidate.height() / 2.0 - input.padY()) / input.scale();
        double width = candidate.width() / input.scale();
        double height = candidate.height() / input.scale();

        int mappedX = (int) Math.max(0, Math.round(x));
        int mappedY = (int) Math.max(0, Math.round(y));
        int mappedWidth = (int) Math.min(cropWidth - mappedX, Math.round(width));
        int mappedHeight = (int) Math.min(cropHeight - mappedY, Math.round(height));
        return new Rectangle(
                cropBounds.x + mappedX,
                cropBounds.y + mappedY,
                Math.max(0, mappedWidth),
                Math.max(0, mappedHeight)
        );
    }

    private static Rectangle clampRegion(RegionConfig region, int width, int height) {
        if (region == null) {
            return new Rectangle(0, 0, width, height);
        }
        int x = Math.max(0, Math.min(region.x(), width - 1));
        int y = Math.max(0, Math.min(region.y(), height - 1));
        int clampedWidth = Math.max(1, Math.min(region.width(), width - x));
        int clampedHeight = Math.max(1, Math.min(region.height(), height - y));
        return new Rectangle(x, y, clampedWidth, clampedHeight);
    }

    record LetterboxResult(float[] tensorInput, int width, int height, double scale, int padX, int padY) {
    }

    record Candidate(float centerX, float centerY, float width, float height, int classId, double score) {
    }

    record RawYoloResult(float[][] predictions, Map<Integer, String> labels) {
    }

    interface YoloInferenceEngine {
        RawYoloResult run(YoloConfig config, float[] input, int width, int height);
    }

    static final class OnnxRuntimeInferenceEngine implements YoloInferenceEngine {
        private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
        private final Map<String, OrtSession> sessionCache = new ConcurrentHashMap<>();
        private final Map<String, Map<Integer, String>> labelCache = new ConcurrentHashMap<>();

        @Override
        public RawYoloResult run(YoloConfig config, float[] input, int width, int height) {
            try {
                OrtSession session = sessionCache.computeIfAbsent(config.modelPath(), this::createSession);
                String inputName = session.getInputNames().iterator().next();
                try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), new long[]{1, 3, height, width});
                     OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
                    float[][] predictions = canonicalize(firstTensor(result));
                    return new RawYoloResult(predictions, loadLabels(config.labelsPath()));
                }
            } catch (OrtException e) {
                throw new IllegalStateException("YOLO inference failed", e);
            }
        }

        private OrtSession createSession(String modelPath) {
            try {
                Path path = Path.of(modelPath);
                if (!Files.exists(path)) {
                    throw new IllegalArgumentException("YOLO model file not found: " + path.toAbsolutePath());
                }
                return environment.createSession(modelPath, new OrtSession.SessionOptions());
            } catch (OrtException e) {
                throw new IllegalStateException("Failed to create ONNX session for " + modelPath, e);
            }
        }

        private Map<Integer, String> loadLabels(String labelsPath) {
            return labelCache.computeIfAbsent(labelsPath == null ? "" : labelsPath, path -> {
                if (path.isBlank()) {
                    return Map.of();
                }
                try {
                    Path labelsFile = Path.of(path);
                    if (!Files.exists(labelsFile)) {
                        throw new IllegalArgumentException("YOLO labels file not found: " + labelsFile.toAbsolutePath());
                    }
                    Map<Integer, String> labels = new LinkedHashMap<>();
                    List<String> lines = Files.readAllLines(labelsFile);
                    for (int i = 0; i < lines.size(); i++) {
                        labels.put(i, lines.get(i).trim());
                    }
                    return labels;
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read YOLO labels: " + path, e);
                }
            });
        }

        private static Object firstTensor(OrtSession.Result result) throws OrtException {
            for (Map.Entry<String, OnnxValue> entry : result) {
                return entry.getValue().getValue();
            }
            throw new IllegalStateException("ONNX result is empty");
        }

        private static float[][] canonicalize(Object rawValue) {
            if (rawValue instanceof float[][][] tensor3d) {
                if (tensor3d.length == 0) {
                    return new float[0][0];
                }
                float[][] matrix = tensor3d[0];
                if (matrix.length == 0) {
                    return matrix;
                }
                if (matrix.length < matrix[0].length) {
                    float[][] transposed = new float[matrix[0].length][matrix.length];
                    for (int row = 0; row < matrix.length; row++) {
                        for (int column = 0; column < matrix[row].length; column++) {
                            transposed[column][row] = matrix[row][column];
                        }
                    }
                    return transposed;
                }
                return matrix;
            }
            if (rawValue instanceof float[][] tensor2d) {
                return tensor2d;
            }
            throw new IllegalStateException("Unsupported ONNX output shape: " + rawValue.getClass().getName());
        }
    }
}
