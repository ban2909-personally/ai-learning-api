package com.ailearning.platform.analytics.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CompletionFact(
        UUID eventId,
        UUID userId,
        UUID enrollmentId,
        UUID courseId,
        UUID lessonId,
        Instant completedAt,
        Instant projectedAt
) {
    public CompletionFact {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(enrollmentId, "enrollmentId is required");
        Objects.requireNonNull(courseId, "courseId is required");
        Objects.requireNonNull(lessonId, "lessonId is required");
        Objects.requireNonNull(completedAt, "completedAt is required");
        Objects.requireNonNull(projectedAt, "projectedAt is required");
    }
}
