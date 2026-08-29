package com.ailearning.platform.catalog.adapter.in.web.dto.response;

import com.ailearning.platform.catalog.domain.model.Course;
import com.ailearning.platform.catalog.domain.enums.CourseLevel;

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
    public static CourseDetailResponse from(Course course) {
        return new CourseDetailResponse(
                course.id(), course.slug(), course.title(), course.shortDescription(),
                course.description(), course.level(), course.language(), course.price(),
                course.currency(), course.thumbnailUrl(), course.estimatedDurationMinutes(),
                CategoryResponse.from(course.category()), course.instructorId(), course.instructorName(), course.publishedAt()
        );
    }
}
