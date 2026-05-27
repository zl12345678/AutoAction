package com.auto.vision;

import com.auto.config.VisionConfig;
import com.auto.window.WindowCaptureFrames;
import com.auto.window.WindowRef;
import com.auto.window.WindowService;
import org.opencv.core.Point;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;

public final class OpenCvNavigationPipeline implements NavigationPipeline, NavigationTickClient {
    private final WindowService windowService;
    private final OpenCvNavigationAnalyzer analyzer;

    public OpenCvNavigationPipeline(WindowService windowService) {
        this(windowService, new OpenCvNavigationAnalyzer());
    }

    OpenCvNavigationPipeline(WindowService windowService, OpenCvNavigationAnalyzer analyzer) {
        this.windowService = windowService;
        this.analyzer = analyzer;
    }

  public OpenCvNavigationAnalyzer analyzer() {
        return analyzer;
    }

    @Override
    public Optional<NavigationStep> planNext(VisionConfig config, WindowRef window, int waypointIndex) {
        WindowCaptureFrames frames = captureWindow(window);
        NavigationAnalysis analysis = analyzer.analyzeWindowCapture(
                config,
                frames.image(),
                frames.windowBoundsOnScreen(),
                frames.clientBoundsOnScreen(),
                window.title(),
                waypointIndex
        );
        if (!analysis.success()) {
            System.out.println("Vision: " + analysis.message());
        }
        return analysis.toNavigationStep();
    }

    @Override
    public Optional<NavigationAnalysis> analyzeNext(VisionConfig config, WindowRef window, int waypointIndex) {
        WindowCaptureFrames frames = captureWindow(window);
        NavigationAnalysis analysis = analyzer.analyzeWindowCapture(
                config,
                frames.image(),
                frames.windowBoundsOnScreen(),
                frames.clientBoundsOnScreen(),
                window.title(),
                waypointIndex
        );
        if (!analysis.success()) {
            System.out.println("Vision: " + analysis.message());
        }
        return Optional.of(analysis);
    }

    private WindowCaptureFrames captureWindow(WindowRef window) {
        return WindowCaptureFrames.capture(windowService, window);
    }

    static Point convertToScreenPoint(Point currentMapPoint, Point targetMapPoint, Point currentScreenPoint, int step) {
        double angle = Math.atan2(targetMapPoint.y - currentMapPoint.y, targetMapPoint.x - currentMapPoint.x);
        return new Point(
                currentScreenPoint.x + step * Math.cos(angle),
                currentScreenPoint.y + step * Math.sin(angle)
        );
    }
}
