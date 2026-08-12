package com.ailearning.platform.catalog.application;

import com.ailearning.platform.catalog.domain.CourseLevel;

import java.math.BigDecimal;

public record CatalogQuery(
        String search,
        String category,
        CourseLevel level,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int page,
        int size
) {
}
