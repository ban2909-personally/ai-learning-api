package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.catalog.api.usecase.learning.CourseLearningContentLookup;
import com.ailearning.platform.learning.api.contract.LessonProgressView;
import com.ailearning.platform.learning.api.usecase.LessonProgressUseCase;
import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.application.port.out.LearningEventOutbox;
import com.ailearning.platform.learning.application.port.out.LessonProgressStore;
import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;
import com.ailearning.platform.learning.domain.event.LessonCompleted;
import com.ailearning.platform.learning.domain.model.LessonProgress;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class LessonProgressService implements LessonProgressUseCase {
    private final CourseLearningContentLookup content;
    private final EnrollmentStore enrollments;
    private final LessonProgressStore progress;
    private final LearningEventOutbox events;
    private final Clock clock;

    public LessonProgressService(
            CourseLearningContentLookup content,
            EnrollmentStore enrollments,
            LessonProgressStore progress,
            LearningEventOutbox events,
            Clock clock
    ) {
        this.content = content;
        this.enrollments = enrollments;
        this.progress = progress;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public LessonProgressView find(UUID userId, String courseSlug, UUID lessonId) {
        var access = access(userId, courseSlug, lessonId);
        return progress.find(access.enrollmentId(), lessonId)
                .map(this::toView)
                .orElseGet(() -> new LessonProgressView(lessonId, 0, false, null));
    }

    @Override
    public LessonProgressView save(
            UUID userId,
            String courseSlug,
            UUID lessonId,
            int positionSeconds,
            boolean completed
    ) {
        var access = access(userId, courseSlug, lessonId);
        if (positionSeconds < 0 || positionSeconds > access.durationSeconds()) {
            throw new BusinessException(
                    "invalid_lesson_position",
                    ErrorType.BAD_REQUEST,
                    "Vị trí xem phải nằm trong thời lượng bài học."
            );
        }

        Optional<LessonProgress> previous = progress.find(access.enrollmentId(), lessonId);
        Instant savedAt = clock.instant();
        var saved = progress.upsert(
                UUID.randomUUID(),
                access.enrollmentId(),
                lessonId,
                positionSeconds,
                completed,
                savedAt
        );

        if (completed && previous.map(value -> !value.completed()).orElse(true)) {
            events.append(new LessonCompleted(
                    UUID.randomUUID(),
                    saved.id(),
                    userId,
                    access.enrollmentId(),
                    access.courseId(),
                    lessonId,
                    savedAt
            ));
        }

        return toView(saved);
    }

    private Access access(UUID userId, String courseSlug, UUID lessonId) {
        var lesson = content.findPublishedLesson(courseSlug, lessonId).orElseThrow(
                () -> new BusinessException("lesson_not_found", ErrorType.NOT_FOUND, "Không tìm thấy bài học.")
        );
        var enrollment = enrollments.find(userId, lesson.courseId()).orElseThrow(
                () -> new BusinessException(
                        "lesson_progress_denied",
                        ErrorType.FORBIDDEN,
                        "Bạn cần ghi danh để lưu tiến độ."
                )
        );
        if (enrollment.status() == EnrollmentStatus.CANCELLED) {
            throw new BusinessException(
                    "lesson_progress_denied",
                    ErrorType.FORBIDDEN,
                    "Ghi danh không còn hiệu lực."
            );
        }
        return new Access(enrollment.id(), lesson.courseId(), lesson.durationSeconds());
    }

    private LessonProgressView toView(LessonProgress value) {
        return new LessonProgressView(
                value.lessonId(),
                value.positionSeconds(),
                value.completed(),
                value.updatedAt()
        );
    }

    private record Access(UUID enrollmentId, UUID courseId, int durationSeconds) {
    }
}
