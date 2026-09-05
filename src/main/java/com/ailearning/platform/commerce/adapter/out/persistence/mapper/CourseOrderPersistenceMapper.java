package com.ailearning.platform.commerce.adapter.out.persistence.mapper;

import com.ailearning.platform.commerce.adapter.out.persistence.jpa.entity.CourseOrderJpaEntity;
import com.ailearning.platform.commerce.domain.model.CourseOrder;
import com.ailearning.platform.commerce.domain.valueobject.Money;

public final class CourseOrderPersistenceMapper {
    private CourseOrderPersistenceMapper() {
    }

    public static CourseOrder toDomain(CourseOrderJpaEntity entity) {
        return new CourseOrder(
                entity.getId(),
                entity.getUserId(),
                entity.getCourseId(),
                entity.getCourseSlug(),
                entity.getCourseTitle(),
                new Money(entity.getAmount(), entity.getCurrency()),
                entity.getIdempotencyKey(),
                entity.getCreatedAt(),
                entity.getExpiresAt()
        );
    }
}
