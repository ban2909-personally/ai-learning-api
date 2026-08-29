package com.ailearning.platform.learning.api.usecase;

import com.ailearning.platform.learning.api.contract.LessonPlayerView;
import java.util.UUID;

public interface LessonAccessUseCase {
    LessonPlayerView openLesson(UUID userId, String courseSlug, UUID lessonId);
}
