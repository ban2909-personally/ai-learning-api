package com.ailearning.platform.catalog.api.contract;

import java.math.BigDecimal;
import java.util.UUID;

public record PublishedCourseView(UUID id, String slug, String title, String shortDescription, String level,
        BigDecimal price, String currency, String thumbnailUrl, int estimatedDurationMinutes,
        CategoryView category, String instructorName) {
    public record CategoryView(UUID id, String slug, String name, String description) {}
}
