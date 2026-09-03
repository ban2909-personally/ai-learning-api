package com.ailearning.platform.catalog.domain.model;

public record LessonMediaAsset(
        String objectKey,
        String contentType,
        long sizeBytes,
        String etag
) {
}
