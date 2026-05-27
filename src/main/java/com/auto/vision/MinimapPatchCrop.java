package com.auto.vision;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;

public record MinimapPatchCrop(Mat patch, Point markerInPatch, Rect sourceRect) {
  public MinimapPatchCrop {
    patch = patch.clone();
  }

  public Point translate(Point pointInSource) {
    if (pointInSource == null) {
      return null;
    }
    return new Point(pointInSource.x - sourceRect.x, pointInSource.y - sourceRect.y);
  }
}
