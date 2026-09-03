package com.ailearning.platform.catalog.api.contract;

import java.util.UUID;

public record LessonMediaView(
        UUID courseId,
        String courseSlug,
        UUID lessonId,
        String contentType,
        long sizeBytes,
        String etag,
        String contentUrl
) {
}
