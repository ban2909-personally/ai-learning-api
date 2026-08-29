package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.catalog.api.contract.PublishedCourseView;
import com.ailearning.platform.catalog.api.usecase.published.PublishedCourseLookup;
import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;
import com.ailearning.platform.learning.domain.model.Enrollment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnrollmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void enrollsThroughModuleApisAndPersistencePort() {
        PublishedCourseLookup courses = mock(PublishedCourseLookup.class);
        UserLookup users = mock(UserLookup.class);
        EnrollmentStore enrollments = mock(EnrollmentStore.class);
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        PublishedCourseView course = new PublishedCourseView(courseId, "java", "Java", "Learn Java", "BEGINNER",
                BigDecimal.ZERO, "VND", null, 60,
                new PublishedCourseView.CategoryView(UUID.randomUUID(), "backend", "Backend", null), "Instructor");
        when(courses.findPublishedBySlug("java")).thenReturn(Optional.of(course));
        when(users.exists(userId)).thenReturn(true);
        when(enrollments.find(userId, courseId)).thenReturn(Optional.of(
                new Enrollment(enrollmentId, userId, courseId, EnrollmentStatus.ACTIVE, NOW, null)));

        var service = new EnrollmentService(courses, users, enrollments, Clock.fixed(NOW, ZoneOffset.UTC));
        var result = service.enroll(userId, "java");

        assertThat(result.id()).isEqualTo(enrollmentId);
        assertThat(result.course()).isEqualTo(course);
        verify(enrollments).insertActiveIfAbsent(any(UUID.class), eq(userId), eq(courseId), eq(NOW));
        verifyNoInteractionsWithRepositoriesOutsidePorts(courses, users, enrollments);
    }

    private void verifyNoInteractionsWithRepositoriesOutsidePorts(PublishedCourseLookup courses, UserLookup users,
                                                                   EnrollmentStore enrollments) {
        verify(courses).findPublishedBySlug("java");
        verify(users).exists(any(UUID.class));
        verify(enrollments).find(any(UUID.class), any(UUID.class));
        verifyNoMoreInteractions(courses, users, enrollments);
    }
}
