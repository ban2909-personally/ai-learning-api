package com.ailearning.platform.catalog.adapter.out.persistence.mapper;

import com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity.CategoryJpaEntity;
import com.ailearning.platform.catalog.adapter.out.persistence.jpa.entity.CourseJpaEntity;
import com.ailearning.platform.catalog.domain.model.Category;
import com.ailearning.platform.catalog.domain.model.Course;

public final class CatalogPersistenceMapper {
    private CatalogPersistenceMapper() {}
    public static Category toDomain(CategoryJpaEntity entity) {
        return new Category(entity.getId(), entity.getSlug(), entity.getName(), entity.getDescription());
    }
    public static Course toDomain(CourseJpaEntity entity) {
        return new Course(entity.getId(), entity.getSlug(), entity.getTitle(), entity.getShortDescription(),
                entity.getDescription(), entity.getLevel(), entity.getLanguage(), entity.getPrice(), entity.getCurrency(),
                entity.getThumbnailUrl(), entity.getEstimatedDurationMinutes(), toDomain(entity.getCategory()),
                entity.getInstructor().getId(), entity.getInstructor().getDisplayName(), entity.getPublishedAt());
    }
}
