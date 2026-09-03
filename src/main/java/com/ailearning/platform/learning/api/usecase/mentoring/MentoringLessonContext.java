package com.ailearning.platform.learning.api.usecase.mentoring;

import java.util.UUID;

public record MentoringLessonContext(
        UUID courseId,
        String courseSlug,
        UUID lessonId,
        String lessonTitle
) {
}
