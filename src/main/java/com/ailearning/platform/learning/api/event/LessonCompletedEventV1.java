package com.ailearning.platform.learning.api.event;

import java.time.Instant;
import java.util.UUID;

public record LessonCompletedEventV1(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID progressId,
        UUID userId,
        UUID enrollmentId,
        UUID courseId,
        UUID lessonId
) {
    public static final String EVENT_TYPE = "lesson.completed";
    public static final int SCHEMA_VERSION = 1;
}
