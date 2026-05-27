package com.auto.vision;

import com.auto.config.ScreenCalibrationConfig;
import com.auto.config.ScreenCalibrationPointConfig;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.List;

public final class ScreenMapper {
    private final boolean enabled;
    private final Mat affineTransform;

    public ScreenMapper(ScreenCalibrationConfig calibration) {
        this.enabled = calibration.enabled() && calibration.points().size() >= 3;
        this.affineTransform = enabled ? buildTransform(calibration.points()) : null;
    }

    public boolean enabled() {
        return enabled;
    }

    public Point mapToScreen(Point mapPoint) {
        if (!enabled || mapPoint == null) {
            return null;
        }
        MatOfPoint2f src = new MatOfPoint2f(mapPoint);
        MatOfPoint2f dst = new MatOfPoint2f();
        Core.transform(src, dst, affineTransform);
        Point[] points = dst.toArray();
        if (points.length == 0) {
            return null;
        }
        return points[0];
    }

  public Point mapToScreen(double mapX, double mapY) {
        return mapToScreen(new Point(mapX, mapY));
    }

    public static Point fallbackScreenPoint(
            Point currentMapPoint,
            Point nextMapPoint,
            Point windowCenter,
            int moveStep
    ) {
        return OpenCvNavigationPipeline.convertToScreenPoint(
                currentMapPoint,
                nextMapPoint,
                windowCenter,
                moveStep
        );
    }

    public static Point applyAngleOffset(Point windowCenter, Point screenPoint, double angleOffsetRadians) {
        if (windowCenter == null || screenPoint == null || angleOffsetRadians == 0.0) {
            return screenPoint;
        }
        double dx = screenPoint.x - windowCenter.x;
        double dy = screenPoint.y - windowCenter.y;
        double radius = Math.hypot(dx, dy);
        if (radius <= 0.0) {
            return screenPoint;
        }
        double angle = Math.atan2(dy, dx) + angleOffsetRadians;
        return new Point(
                windowCenter.x + radius * Math.cos(angle),
                windowCenter.y + radius * Math.sin(angle)
        );
    }

    private static Mat buildTransform(List<ScreenCalibrationPointConfig> points) {
        Point[] mapPoints = new Point[3];
        Point[] screenPoints = new Point[3];
        for (int i = 0; i < 3; i++) {
            ScreenCalibrationPointConfig point = points.get(i);
            mapPoints[i] = new Point(point.mapX(), point.mapY());
            screenPoints[i] = new Point(point.screenX(), point.screenY());
        }
        return Imgproc.getAffineTransform(
                new MatOfPoint2f(mapPoints),
                new MatOfPoint2f(screenPoints)
        );
    }
}
