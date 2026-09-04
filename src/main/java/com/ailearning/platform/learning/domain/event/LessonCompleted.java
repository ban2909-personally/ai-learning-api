package com.ailearning.platform.learning.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LessonCompleted(
        UUID eventId,
        UUID progressId,
        UUID userId,
        UUID enrollmentId,
        UUID courseId,
        UUID lessonId,
        Instant occurredAt
) {
    public LessonCompleted {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(progressId, "progressId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(enrollmentId, "enrollmentId is required");
        Objects.requireNonNull(courseId, "courseId is required");
        Objects.requireNonNull(lessonId, "lessonId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }
}
