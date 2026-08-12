package com.ailearning.platform.catalog.api;

import com.ailearning.platform.catalog.domain.CourseEntity;
import com.ailearning.platform.catalog.domain.CourseLevel;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseSummaryResponse(
        UUID id,
        String slug,
        String title,
        String shortDescription,
        CourseLevel level,
        BigDecimal price,
        String currency,
        String thumbnailUrl,
        int estimatedDurationMinutes,
        CategoryResponse category,
        String instructorName
) {
    public static CourseSummaryResponse from(CourseEntity course) {
        return new CourseSummaryResponse(
                course.getId(), course.getSlug(), course.getTitle(), course.getShortDescription(),
                course.getLevel(), course.getPrice(), course.getCurrency(), course.getThumbnailUrl(),
                course.getEstimatedDurationMinutes(), CategoryResponse.from(course.getCategory()),
                course.getInstructor().getDisplayName()
        );
    }
}
