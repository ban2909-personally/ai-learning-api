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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MentoringLessonContextServiceTest {
    private final CourseLearningContentLookup content = mock(CourseLearningContentLookup.class);
    private final EnrollmentStore enrollments = mock(EnrollmentStore.class);
    private final MentoringLessonContextService service = new MentoringLessonContextService(content, enrollments);

    @Test
    void returnsMinimalLessonContextForAnEnrolledLearner() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        when(content.findPublishedLesson("clean-spring", lessonId)).thenReturn(Optional.of(
                new LessonContentView(
                        courseId,
                        "clean-spring",
                        UUID.randomUUID(),
                        lessonId,
                        "Dependency inversion",
                        "/api/v1/media/example",
                        600,
                        false
                )
        ));
        when(enrollments.find(userId, courseId)).thenReturn(Optional.of(new Enrollment(
                UUID.randomUUID(), userId, courseId, EnrollmentStatus.ACTIVE, Instant.now(), null
        )));

        var context = service.requireAccessible(userId, "clean-spring", lessonId);

        assertThat(context.courseId()).isEqualTo(courseId);
        assertThat(context.lessonId()).isEqualTo(lessonId);
        assertThat(context.lessonTitle()).isEqualTo("Dependency inversion");
    }

    @Test
    void rejectsPreviewLessonWhenLearnerIsNotEnrolled() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        when(content.findPublishedLesson("clean-spring", lessonId)).thenReturn(Optional.of(
                new LessonContentView(
                        courseId,
                        "clean-spring",
                        UUID.randomUUID(),
                        lessonId,
                        "Preview",
                        "https://video.example/preview",
                        60,
                        true
                )
        ));
        when(enrollments.find(userId, courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireAccessible(userId, "clean-spring", lessonId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("mentor_access_denied");
    }
}

