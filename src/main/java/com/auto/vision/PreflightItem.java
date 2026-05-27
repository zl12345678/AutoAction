package com.auto.vision;

public record PreflightItem(
        String id,
        String title,
        PreflightLevel level,
        String detail
) {
}
