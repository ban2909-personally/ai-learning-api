package com.ailearning.platform.analytics.adapter.in.web.dto.response;

import com.ailearning.platform.analytics.api.contract.LearningAnalyticsView;

import java.time.Instant;
import java.util.List;

public record LearningAnalyticsResponse(
        long completedLessons,
        long coursesWithCompletions,
        Instant lastCompletedAt,
        List<CourseCompletionResponse> courses
) {
    public static LearningAnalyticsResponse from(LearningAnalyticsView view) {
        return new LearningAnalyticsResponse(
                view.completedLessons(),
                view.coursesWithCompletions(),
                view.lastCompletedAt(),
                view.courses().stream().map(CourseCompletionResponse::from).toList()
        );
    }
}
