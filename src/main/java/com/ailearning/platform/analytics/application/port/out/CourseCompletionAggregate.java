package com.ailearning.platform.analytics.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record CourseCompletionAggregate(
        UUID courseId,
        long completedLessons,
        Instant lastCompletedAt
) {
}
