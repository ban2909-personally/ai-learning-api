package com.ailearning.platform.catalog.api;

import com.ailearning.platform.catalog.domain.CourseEntity;
import com.ailearning.platform.catalog.domain.CourseLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseDetailResponse(
        UUID id,
        String slug,
        String title,
        String shortDescription,
        String description,
        CourseLevel level,
        String language,
        BigDecimal price,
        String currency,
        String thumbnailUrl,
        int estimatedDurationMinutes,
        CategoryResponse category,
        UUID instructorId,
        String instructorName,
        Instant publishedAt
) {
    public static CourseDetailResponse from(CourseEntity course) {
        return new CourseDetailResponse(
                course.getId(), course.getSlug(), course.getTitle(), course.getShortDescription(),
                course.getDescription(), course.getLevel(), course.getLanguage(), course.getPrice(),
                course.getCurrency(), course.getThumbnailUrl(), course.getEstimatedDurationMinutes(),
                CategoryResponse.from(course.getCategory()), course.getInstructor().getId(),
                course.getInstructor().getDisplayName(), course.getPublishedAt()
        );
    }
}
