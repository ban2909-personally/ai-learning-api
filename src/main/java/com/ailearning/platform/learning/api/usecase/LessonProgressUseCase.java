package com.ailearning.platform.learning.api.usecase;

import com.ailearning.platform.learning.api.contract.LessonProgressView;
import java.util.UUID;

public interface LessonProgressUseCase {
    LessonProgressView find(UUID userId, String courseSlug, UUID lessonId);
    LessonProgressView save(UUID userId, String courseSlug, UUID lessonId, int positionSeconds, boolean completed);
}
