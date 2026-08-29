package com.ailearning.platform.catalog.adapter.in.web.dto.response;

import com.ailearning.platform.catalog.domain.model.Course;
import com.ailearning.platform.catalog.domain.enums.CourseLevel;

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
    public static CourseSummaryResponse from(Course course) {
        return new CourseSummaryResponse(
                course.id(), course.slug(), course.title(), course.shortDescription(),
                course.level(), course.price(), course.currency(), course.thumbnailUrl(),
                course.estimatedDurationMinutes(), CategoryResponse.from(course.category()), course.instructorName()
        );
    }
}
