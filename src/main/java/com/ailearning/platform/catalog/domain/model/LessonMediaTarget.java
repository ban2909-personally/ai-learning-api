package com.ailearning.platform.catalog.domain.model;

import java.util.UUID;

public record LessonMediaTarget(
        UUID courseId,
        String courseSlug,
        UUID lessonId,
        UUID instructorId
) {
}
