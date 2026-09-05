package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;
import com.ailearning.platform.learning.domain.model.Enrollment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrollmentAccessServiceTest {
    private final EnrollmentStore enrollments = mock(EnrollmentStore.class);
    private final EnrollmentAccessService service = new EnrollmentAccessService(enrollments);
    private final UUID userId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @Test
    void grantsAccessForAnActiveEnrollment() {
        when(enrollments.find(userId, courseId)).thenReturn(Optional.of(enrollment(EnrollmentStatus.ACTIVE)));

        assertThat(service.hasLearningAccess(userId, courseId)).isTrue();

        verify(enrollments).find(userId, courseId);
    }

    @Test
    void deniesAccessWhenEnrollmentIsMissingOrCancelled() {
        when(enrollments.find(userId, courseId)).thenReturn(Optional.empty());
        assertThat(service.hasLearningAccess(userId, courseId)).isFalse();

        when(enrollments.find(userId, courseId)).thenReturn(Optional.of(enrollment(EnrollmentStatus.CANCELLED)));
        assertThat(service.hasLearningAccess(userId, courseId)).isFalse();
    }

    private Enrollment enrollment(EnrollmentStatus status) {
        return new Enrollment(UUID.randomUUID(), userId, courseId, status, Instant.parse("2026-09-05T00:00:00Z"), null);
    }
}
