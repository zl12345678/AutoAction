package com.auto.vision;

import com.auto.opencv.utils.ImageProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class NavigationDebugArtifactWriter {
    private static final DateTimeFormatter SESSION_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path sessionDir;

    public NavigationDebugArtifactWriter() {
        this(defaultSessionDir());
    }

    NavigationDebugArtifactWriter(Path sessionDir) {
        this.sessionDir = sessionDir;
    }

    public Path sessionDir() {
        return sessionDir;
    }

    public Path writeStep(NavigationDebugStep step, List<NavigationDebugArtifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return null;
        }
        Path stepDir = sessionDir.resolve(stepFileName(step));
        try {
            Files.createDirectories(stepDir);
            for (NavigationDebugArtifact artifact : artifacts) {
                if (artifact.image() == null) {
                    continue;
                }
                Path file = stepDir.resolve(sanitize(artifact.id()) + ".png");
                ImageIO.write(artifact.image(), "png", file.toFile());
            }
            return stepDir;
        } catch (IOException exception) {
            return null;
        }
    }

    public void resetSession() {
        // New session directory on next writer instance; caller replaces writer when resetting debug.
    }

    public static NavigationDebugArtifactWriter openNewSession() {
        return new NavigationDebugArtifactWriter(defaultSessionDir());
    }

    private static Path defaultSessionDir() {
        String stamp = LocalDateTime.now().format(SESSION_FORMAT);
        Path base = Path.of(System.getProperty("user.dir", "."), "navigation-debug", stamp);
        try {
            Files.createDirectories(base);
        } catch (IOException ignored) {
            // Fall back to in-memory only if disk is unavailable.
        }
        return base;
    }

    private static String stepFileName(NavigationDebugStep step) {
        return String.format("%02d-%s", step.order(), step.name().toLowerCase());
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    static BufferedImage copyImage(BufferedImage image) {
        return image == null ? null : ImageProcessor.copyBufferedImage(image);
    }
}
