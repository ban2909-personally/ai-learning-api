package com.ailearning.platform.analytics.api.contract;

import java.time.Instant;
import java.util.UUID;

public record ProjectLessonCompletionCommand(
        UUID eventId,
        UUID userId,
        UUID enrollmentId,
        UUID courseId,
        UUID lessonId,
        Instant occurredAt
) {
}
