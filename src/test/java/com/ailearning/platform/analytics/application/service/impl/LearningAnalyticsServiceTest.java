package com.ailearning.platform.analytics.application.service.impl;

import com.ailearning.platform.analytics.api.contract.ProjectLessonCompletionCommand;
import com.ailearning.platform.analytics.application.port.out.CompletionAnalyticsSnapshot;
import com.ailearning.platform.analytics.application.port.out.CompletionAnalyticsStore;
import com.ailearning.platform.analytics.application.port.out.CourseCompletionAggregate;
import com.ailearning.platform.analytics.domain.model.CompletionFact;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningAnalyticsServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private final CompletionAnalyticsStore store = mock(CompletionAnalyticsStore.class);
    private final LearningAnalyticsService service = new LearningAnalyticsService(
            store,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void projectsACompletionFactWithServerProjectionTime() {
        var command = command();
        when(store.append(any())).thenReturn(true);

        assertThat(service.projectLessonCompleted(command)).isTrue();

        verify(store).append(new CompletionFact(
                command.eventId(),
                command.userId(),
                command.enrollmentId(),
                command.courseId(),
                command.lessonId(),
                command.occurredAt(),
                NOW
        ));
    }

    @Test
    void reportsDuplicateProjectionWithoutChangingTheEventIdentity() {
        var command = command();
        when(store.append(any())).thenReturn(false);

        assertThat(service.projectLessonCompleted(command)).isFalse();
    }

    @Test
    void mapsABoundedUserSnapshotToThePublicContract() {
        UUID courseId = UUID.randomUUID();
        when(store.summarize(USER_ID, 20)).thenReturn(new CompletionAnalyticsSnapshot(
                3,
                1,
                NOW,
                List.of(new CourseCompletionAggregate(courseId, 3, NOW))
        ));

        var view = service.findMine(USER_ID, 20);

        assertThat(view.completedLessons()).isEqualTo(3);
        assertThat(view.coursesWithCompletions()).isOne();
        assertThat(view.lastCompletedAt()).isEqualTo(NOW);
        assertThat(view.courses()).singleElement().satisfies(course -> {
            assertThat(course.courseId()).isEqualTo(courseId);
            assertThat(course.completedLessons()).isEqualTo(3);
        });
    }

    @Test
    void rejectsAnUnboundedCourseLimitBeforeCallingPersistence() {
        assertThatThrownBy(() -> service.findMine(USER_ID, LearningAnalyticsService.MAX_COURSE_LIMIT + 1))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("invalid_analytics_course_limit");
    }

    private ProjectLessonCompletionCommand command() {
        return new ProjectLessonCompletionCommand(
                UUID.randomUUID(),
                USER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW.minusSeconds(5)
        );
    }
}
