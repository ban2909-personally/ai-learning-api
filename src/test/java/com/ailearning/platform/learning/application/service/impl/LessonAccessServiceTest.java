package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.catalog.api.contract.LessonContentView;
import com.ailearning.platform.catalog.api.usecase.learning.CourseLearningContentLookup;
import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;
import com.ailearning.platform.learning.domain.model.Enrollment;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LessonAccessServiceTest {
    private final CourseLearningContentLookup content = mock(CourseLearningContentLookup.class);
    private final EnrollmentStore enrollments = mock(EnrollmentStore.class);
    private final LessonAccessService service = new LessonAccessService(content, enrollments);
    private final UUID userId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID lessonId = UUID.randomUUID();

    @Test void opensPreviewWithoutEnrollmentLookup() {
        when(content.findPublishedLesson("spring", lessonId)).thenReturn(Optional.of(lesson(true)));
        var result = service.openLesson(userId, "spring", lessonId);
        assertThat(result.contentUrl()).isEqualTo("https://content.test/lesson");
        verifyNoInteractions(enrollments);
    }

    @Test void rejectsLockedLessonWithoutEnrollment() {
        when(content.findPublishedLesson("spring", lessonId)).thenReturn(Optional.of(lesson(false)));
        when(enrollments.find(userId, courseId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.openLesson(userId, "spring", lessonId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("lesson_access_denied"));
    }

    @Test void opensLockedLessonForActiveEnrollment() {
        when(content.findPublishedLesson("spring", lessonId)).thenReturn(Optional.of(lesson(false)));
        when(enrollments.find(userId, courseId)).thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE)));
        assertThat(service.openLesson(userId, "spring", lessonId).lessonId()).isEqualTo(lessonId);
    }

    private LessonContentView lesson(boolean preview) {
        return new LessonContentView(courseId, "spring", UUID.randomUUID(), lessonId, "Lesson",
                "https://content.test/lesson", 120, preview);
    }
    private Enrollment enrollment(EnrollmentStatus status) {
        return new Enrollment(UUID.randomUUID(), userId, courseId, status, Instant.now(), null);
    }
}
