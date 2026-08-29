package com.ailearning.platform.learning.adapter.in.web.dto.response;

import com.ailearning.platform.learning.api.contract.EnrollmentView;
import com.ailearning.platform.learning.domain.enums.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(UUID id, EnrollmentStatus status, Instant enrolledAt, LearningCourseResponse course) {
    public static EnrollmentResponse from(EnrollmentView enrollment) {
        return new EnrollmentResponse(enrollment.id(), enrollment.status(), enrollment.enrolledAt(),
                LearningCourseResponse.from(enrollment.course()));
    }
}
