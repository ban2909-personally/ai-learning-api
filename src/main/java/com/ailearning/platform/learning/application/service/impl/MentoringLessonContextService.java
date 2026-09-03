package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.catalog.api.usecase.learning.CourseLearningContentLookup;
import com.ailearning.platform.learning.api.usecase.mentoring.MentoringLessonContext;
import com.ailearning.platform.learning.api.usecase.mentoring.MentoringLessonContextLookup;
import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.UUID;

public class MentoringLessonContextService implements MentoringLessonContextLookup {
    private final CourseLearningContentLookup content;
    private final EnrollmentStore enrollments;

    public MentoringLessonContextService(CourseLearningContentLookup content, EnrollmentStore enrollments) {
        this.content = content;
        this.enrollments = enrollments;
    }

    @Override
    public MentoringLessonContext requireAccessible(UUID userId, String courseSlug, UUID lessonId) {
        var lesson = content.findPublishedLesson(courseSlug, lessonId).orElseThrow(() ->
                new BusinessException("lesson_not_found", ErrorType.NOT_FOUND, "Không tìm thấy bài học."));
        var enrollment = enrollments.find(userId, lesson.courseId()).orElseThrow(() ->
                new BusinessException(
                        "mentor_access_denied",
                        ErrorType.FORBIDDEN,
                        "Bạn cần ghi danh để sử dụng AI Mentor cho bài học này."
                ));
        if (enrollment.status() == EnrollmentStatus.CANCELLED) {
            throw new BusinessException(
                    "mentor_access_denied",
                    ErrorType.FORBIDDEN,
                    "Ghi danh không còn hiệu lực."
            );
        }
        return new MentoringLessonContext(
                lesson.courseId(),
                lesson.courseSlug(),
                lesson.lessonId(),
                lesson.title()
        );
    }
}

