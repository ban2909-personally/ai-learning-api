package com.ailearning.platform.analytics.application.port.out;

import java.time.Instant;
import java.util.List;

public record CompletionAnalyticsSnapshot(
        long completedLessons,
        long coursesWithCompletions,
        Instant lastCompletedAt,
        List<CourseCompletionAggregate> courses
) {
    public CompletionAnalyticsSnapshot {
        courses = List.copyOf(courses);
    }

    public static CompletionAnalyticsSnapshot empty() {
        return new CompletionAnalyticsSnapshot(0, 0, null, List.of());
    }
}
