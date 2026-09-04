package com.ailearning.platform.analytics.api.contract;

import java.time.Instant;
import java.util.UUID;

public record CourseCompletionView(
        UUID courseId,
        long completedLessons,
        Instant lastCompletedAt
) {
}
