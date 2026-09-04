package com.ailearning.platform.analytics.adapter.in.web.dto.response;

import com.ailearning.platform.analytics.api.contract.CourseCompletionView;

import java.time.Instant;
import java.util.UUID;

public record CourseCompletionResponse(
        UUID courseId,
        long completedLessons,
        Instant lastCompletedAt
) {
    static CourseCompletionResponse from(CourseCompletionView view) {
        return new CourseCompletionResponse(
                view.courseId(),
                view.completedLessons(),
                view.lastCompletedAt()
        );
    }
}
