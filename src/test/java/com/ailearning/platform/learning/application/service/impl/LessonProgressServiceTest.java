package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.catalog.api.contract.LessonContentView;
import com.ailearning.platform.catalog.api.usecase.learning.CourseLearningContentLookup;
import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.application.port.out.LessonProgressStore;
import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;
import com.ailearning.platform.learning.domain.model.Enrollment;
import com.ailearning.platform.learning.domain.model.LessonProgress;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LessonProgressServiceTest {
    private final CourseLearningContentLookup content = mock(CourseLearningContentLookup.class);
    private final EnrollmentStore enrollments = mock(EnrollmentStore.class);
    private final LessonProgressStore progress = mock(LessonProgressStore.class);
    private final Instant now = Instant.parse("2026-08-30T00:00:00Z");
    private final LessonProgressService service = new LessonProgressService(content, enrollments, progress,
            Clock.fixed(now, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID lessonId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();

    @Test void returnsEmptyResumeStateWhenNoProgressExists() {
        allowAccess();
        when(progress.find(enrollmentId, lessonId)).thenReturn(Optional.empty());
        var result = service.find(userId, "spring", lessonId);
        assertThat(result.positionSeconds()).isZero();
        assertThat(result.completed()).isFalse();
    }

    @Test void rejectsPositionBeyondLessonDuration() {
        allowAccess();
        assertThatThrownBy(() -> service.save(userId, "spring", lessonId, 121, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("invalid_lesson_position"));
        verifyNoInteractions(progress);
    }

    @Test void persistsValidProgressWithUtcTimestamp() {
        allowAccess();
        when(progress.upsert(any(UUID.class), eq(enrollmentId), eq(lessonId), eq(45), eq(true), eq(now)))
                .thenAnswer(invocation -> new LessonProgress(invocation.getArgument(0), enrollmentId, lessonId, 45, true, now));
        var result = service.save(userId, "spring", lessonId, 45, true);
        assertThat(result).extracting(value -> value.positionSeconds(), value -> value.completed(), value -> value.updatedAt())
                .containsExactly(45, true, now);
    }

    private void allowAccess() {
        when(content.findPublishedLesson("spring", lessonId)).thenReturn(Optional.of(new LessonContentView(
                courseId, "spring", UUID.randomUUID(), lessonId, "Lesson", "https://content.test", 120, false)));
        when(enrollments.find(userId, courseId)).thenReturn(Optional.of(new Enrollment(
                enrollmentId, userId, courseId, EnrollmentStatus.ACTIVE, now, null)));
    }
}
