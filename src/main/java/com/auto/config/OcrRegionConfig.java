package com.auto.config;

import java.util.Objects;

public record OcrRegionConfig(
        String name,
        OcrRegionSource source,
        RegionConfig region,
        double scale,
        Integer threshold,
        String whitelist
) {
    public OcrRegionConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ocr region name is required");
        }
        source = Objects.requireNonNull(source, "source");
        region = Objects.requireNonNull(region, "region");
        if (scale <= 0.0) {
            throw new IllegalArgumentException("ocr region scale must be positive");
        }
        whitelist = whitelist == null ? "" : whitelist;
    }
}
