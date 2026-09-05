package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.learning.api.usecase.access.EnrollmentAccessLookup;
import com.ailearning.platform.learning.application.port.out.EnrollmentStore;
import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;

import java.util.Objects;
import java.util.UUID;

public class EnrollmentAccessService implements EnrollmentAccessLookup {
    private final EnrollmentStore enrollments;

    public EnrollmentAccessService(EnrollmentStore enrollments) {
        this.enrollments = Objects.requireNonNull(enrollments, "enrollments is required");
    }

    @Override
    public boolean hasLearningAccess(UUID userId, UUID courseId) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(courseId, "courseId is required");
        return enrollments.find(userId, courseId)
                .map(enrollment -> enrollment.status() != EnrollmentStatus.CANCELLED)
                .orElse(false);
    }
}
