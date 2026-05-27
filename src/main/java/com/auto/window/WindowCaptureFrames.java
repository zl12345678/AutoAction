package com.auto.window;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Window capture uses the full window bitmap so configured minimap ROI stays valid.
 * Client bounds are kept separately for click targeting inside the playable area.
 */
public record WindowCaptureFrames(
        BufferedImage image,
        Rectangle windowBoundsOnScreen,
        Rectangle clientBoundsOnScreen
) {
    public static WindowCaptureFrames capture(WindowService windowService, WindowRef window) {
        Rectangle windowBounds = window.bounds();
        Rectangle clientBounds = windowService.getClientBoundsOnScreen(window);
        BufferedImage image = windowService.capture(windowBounds);
        return new WindowCaptureFrames(image, windowBounds, clientBounds);
    }
}
