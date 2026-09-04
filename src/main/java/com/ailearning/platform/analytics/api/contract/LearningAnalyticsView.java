package com.ailearning.platform.analytics.api.contract;

import java.time.Instant;
import java.util.List;

public record LearningAnalyticsView(
        long completedLessons,
        long coursesWithCompletions,
        Instant lastCompletedAt,
        List<CourseCompletionView> courses
) {
    public LearningAnalyticsView {
        courses = List.copyOf(courses);
    }
}
