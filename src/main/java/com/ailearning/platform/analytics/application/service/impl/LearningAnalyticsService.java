package com.ailearning.platform.analytics.application.service.impl;

import com.ailearning.platform.analytics.api.contract.CourseCompletionView;
import com.ailearning.platform.analytics.api.contract.LearningAnalyticsView;
import com.ailearning.platform.analytics.api.contract.ProjectLessonCompletionCommand;
import com.ailearning.platform.analytics.api.usecase.LearningAnalyticsUseCase;
import com.ailearning.platform.analytics.application.port.out.CompletionAnalyticsStore;
import com.ailearning.platform.analytics.domain.model.CompletionFact;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.time.Clock;
import java.util.UUID;

public class LearningAnalyticsService implements LearningAnalyticsUseCase {
    static final int MAX_COURSE_LIMIT = 100;

    private final CompletionAnalyticsStore analytics;
    private final Clock clock;

    public LearningAnalyticsService(CompletionAnalyticsStore analytics, Clock clock) {
        this.analytics = analytics;
        this.clock = clock;
    }

    @Override
    public boolean projectLessonCompleted(ProjectLessonCompletionCommand command) {
        return analytics.append(new CompletionFact(
                command.eventId(),
                command.userId(),
                command.enrollmentId(),
                command.courseId(),
                command.lessonId(),
                command.occurredAt(),
                clock.instant()
        ));
    }

    @Override
    public LearningAnalyticsView findMine(UUID userId, int courseLimit) {
        requireBoundedLimit(courseLimit);
        var snapshot = analytics.summarize(userId, courseLimit);
        var courses = snapshot.courses().stream()
                .map(course -> new CourseCompletionView(
                        course.courseId(),
                        course.completedLessons(),
                        course.lastCompletedAt()
                ))
                .toList();
        return new LearningAnalyticsView(
                snapshot.completedLessons(),
                snapshot.coursesWithCompletions(),
                snapshot.lastCompletedAt(),
                courses
        );
    }

    private void requireBoundedLimit(int courseLimit) {
        if (courseLimit < 1 || courseLimit > MAX_COURSE_LIMIT) {
            throw new BusinessException(
                    "invalid_analytics_course_limit",
                    ErrorType.BAD_REQUEST,
                    "Số khóa học thống kê phải từ 1 đến 100."
            );
        }
    }
}
