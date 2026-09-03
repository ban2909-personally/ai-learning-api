package com.ailearning.platform.catalog.domain.model;

import java.util.UUID;

public record PublishedLessonMedia(
        UUID courseId,
        String courseSlug,
        UUID lessonId,
        LessonMediaAsset asset
) {
}
