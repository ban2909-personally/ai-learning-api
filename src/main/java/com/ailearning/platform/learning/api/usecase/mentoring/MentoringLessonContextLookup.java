package com.ailearning.platform.learning.api.usecase.mentoring;

import java.util.UUID;

public interface MentoringLessonContextLookup {
    MentoringLessonContext requireAccessible(UUID userId, String courseSlug, UUID lessonId);
}
