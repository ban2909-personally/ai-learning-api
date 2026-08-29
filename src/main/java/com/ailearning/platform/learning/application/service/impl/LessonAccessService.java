package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.catalog.api.usecase.learning.CourseLearningContentLookup;
import com.ailearning.platform.learning.api.contract.LessonPlayerView;
import com.ailearning.platform.learning.api.usecase.LessonAccessUseCase;
import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;
import java.util.UUID;

public class LessonAccessService implements LessonAccessUseCase {
    private final CourseLearningContentLookup content;
    private final EnrollmentStore enrollments;
    public LessonAccessService(CourseLearningContentLookup content, EnrollmentStore enrollments) {
        this.content = content; this.enrollments = enrollments;
    }
    @Override public LessonPlayerView openLesson(UUID userId, String courseSlug, UUID lessonId) {
        var lesson = content.findPublishedLesson(courseSlug, lessonId).orElseThrow(() ->
                new BusinessException("lesson_not_found", ErrorType.NOT_FOUND, "Không tìm thấy bài học."));
        if (!lesson.preview()) {
            var enrollment = enrollments.find(userId, lesson.courseId()).orElseThrow(() ->
                    new BusinessException("lesson_access_denied", ErrorType.FORBIDDEN, "Bạn cần ghi danh để xem bài học này."));
            if (enrollment.status() == EnrollmentStatus.CANCELLED) {
                throw new BusinessException("lesson_access_denied", ErrorType.FORBIDDEN, "Ghi danh không còn hiệu lực.");
            }
        }
        return new LessonPlayerView(lesson.courseId(), lesson.courseSlug(), lesson.sectionId(), lesson.lessonId(),
                lesson.title(), lesson.contentUrl(), lesson.durationSeconds(), lesson.preview());
    }
}
