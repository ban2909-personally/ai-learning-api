package com.ailearning.platform.catalog.api.contract;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCourseCommand(
        UUID categoryId,
        String slug,
        String title,
        String shortDescription,
        String description,
        String level,
        BigDecimal price) {}
