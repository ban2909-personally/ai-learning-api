package com.ailearning.platform.learning.adapter.in.web.dto.response;

import com.ailearning.platform.catalog.api.contract.PublishedCourseView;
import java.math.BigDecimal;
import java.util.UUID;

public record LearningCourseResponse(UUID id, String slug, String title, String shortDescription,
        String level, BigDecimal price, String currency, String thumbnailUrl,
        int estimatedDurationMinutes, CategoryResponse category, String instructorName) {
    public static LearningCourseResponse from(PublishedCourseView course) {
        return new LearningCourseResponse(course.id(), course.slug(), course.title(), course.shortDescription(),
                course.level(), course.price(), course.currency(), course.thumbnailUrl(), course.estimatedDurationMinutes(),
                new CategoryResponse(course.category().id(), course.category().slug(), course.category().name(), course.category().description()),
                course.instructorName());
    }
    public record CategoryResponse(UUID id, String slug, String name, String description) {}
}
