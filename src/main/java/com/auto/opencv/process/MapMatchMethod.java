package com.auto.opencv.process;

public enum MapMatchMethod {
    NONE,
    ORB_HOMOGRAPHY,
    SIFT_HOMOGRAPHY,
    /** @deprecated use {@link #ORB_HOMOGRAPHY} or {@link #SIFT_HOMOGRAPHY} */
    @Deprecated
    SIFT_AFFINE,
    /** @deprecated template matching removed */
    @Deprecated
    TEMPLATE_MULTISCALE
}
