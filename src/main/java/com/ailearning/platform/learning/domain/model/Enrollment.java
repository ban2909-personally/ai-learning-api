package com.ailearning.platform.learning.domain.model;

import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record Enrollment(UUID id, UUID userId, UUID courseId, EnrollmentStatus status,
                         Instant enrolledAt, Instant completedAt) {
}
