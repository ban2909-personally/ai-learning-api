package com.ailearning.platform.learning.application.port.out;

import com.ailearning.platform.learning.domain.model.LessonProgress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressStore {
    Optional<LessonProgress> find(UUID enrollmentId, UUID lessonId);
    LessonProgress upsert(UUID id, UUID enrollmentId, UUID lessonId, int positionSeconds, boolean completed, Instant updatedAt);
}
