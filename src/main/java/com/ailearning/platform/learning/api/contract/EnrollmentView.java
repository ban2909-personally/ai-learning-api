package com.ailearning.platform.learning.api.contract;

import com.ailearning.platform.catalog.api.contract.PublishedCourseView;
import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentView(UUID id, EnrollmentStatus status, Instant enrolledAt, Instant completedAt, PublishedCourseView course) {
}
