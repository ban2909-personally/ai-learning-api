package com.ailearning.platform.catalog.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManagedCourse(
        UUID id,
        UUID instructorId,
        UUID categoryId,
        String slug,
        String title,
        String shortDescription,
        String description,
        String level,
        BigDecimal price,
        String status,
        int lessonCount,
        Instant updatedAt) {}
