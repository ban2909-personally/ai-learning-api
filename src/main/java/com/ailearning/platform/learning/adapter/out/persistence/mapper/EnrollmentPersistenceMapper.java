package com.ailearning.platform.learning.adapter.out.persistence.mapper;

import com.ailearning.platform.learning.adapter.out.persistence.jpa.entity.EnrollmentJpaEntity;
import com.ailearning.platform.learning.domain.model.Enrollment;

public final class EnrollmentPersistenceMapper {
    private EnrollmentPersistenceMapper() {}
    public static Enrollment toDomain(EnrollmentJpaEntity entity) {
        return new Enrollment(entity.getId(), entity.getUserId(), entity.getCourseId(), entity.getStatus(),
                entity.getEnrolledAt(), entity.getCompletedAt());
    }
}
