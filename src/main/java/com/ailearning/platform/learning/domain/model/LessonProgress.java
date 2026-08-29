package com.ailearning.platform.learning.domain.model;

import java.time.Instant;
import java.util.UUID;

public record LessonProgress(UUID id, UUID enrollmentId, UUID lessonId, int positionSeconds,
        boolean completed, Instant updatedAt) {}
