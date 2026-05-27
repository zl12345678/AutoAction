package com.auto.config;

import java.util.List;
import java.util.Objects;

public record OcrConfig(
        boolean enabled,
        String language,
        int pageSegMode,
        String defaultWhitelist,
        List<OcrRegionConfig> regions
) {
    public static OcrConfig disabled() {
        return new OcrConfig(false, "eng", 7, "", List.of());
    }

    public OcrConfig {
        language = language == null || language.isBlank() ? "eng" : language;
        if (pageSegMode < 0) {
            throw new IllegalArgumentException("ocr pageSegMode must be non-negative");
        }
        defaultWhitelist = defaultWhitelist == null ? "" : defaultWhitelist;
        Objects.requireNonNull(regions, "regions");
        regions = List.copyOf(regions);
    }
}
