package com.auto.vision;

import com.auto.config.MapClosureConfig;
import com.auto.config.VisionConfig;
import com.auto.opencv.utils.ImageProcessor;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

/**
 * Builds the grayscale pathfinding map used by A* (with mapClosure applied).
 */
public final class PathfindingMapLoader {
    private PathfindingMapLoader() {
    }

    public static Mat build(VisionConfig config) {
        Mat loaded = ImageProcessor.loadMapImage(config.mapImage());
        Mat gray = toSingleChannel(loaded);
        if (loaded != gray) {
            loaded.release();
        }
        Mat pathfinding = gray.clone();
        applyClosure(pathfinding, config.mapClosure());
        return pathfinding;
    }

    static String cacheKey(VisionConfig config) {
        MapClosureConfig closure = config.mapClosure();
        return config.mapImage()
                + "|c=" + closure.walkableThreshold()
                + "|s=" + closure.sealExterior()
                + "|b=" + closure.sealBorderWidth()
                + "|k=" + closure.morphCloseKernelSize();
    }

    private static Mat toSingleChannel(Mat source) {
        if (source.channels() == 1) {
            return source;
        }
        Mat gray = new Mat();
        Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);
        return gray;
    }

    private static void applyClosure(Mat map, MapClosureConfig closure) {
        PathfindingMapPostProcessor.apply(
                map,
                closure.morphCloseKernelSize(),
                closure.sealBorderWidth(),
                closure.sealExterior(),
                closure.walkableThreshold()
        );
    }
}
