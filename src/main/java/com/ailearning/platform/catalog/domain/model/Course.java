package com.ailearning.platform.catalog.domain.model;

import com.ailearning.platform.catalog.domain.enums.CourseLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Course(UUID id, String slug, String title, String shortDescription, String description,
                     CourseLevel level, String language, BigDecimal price, String currency,
                     String thumbnailUrl, int estimatedDurationMinutes, Category category,
                     UUID instructorId, String instructorName, Instant publishedAt) {
}
