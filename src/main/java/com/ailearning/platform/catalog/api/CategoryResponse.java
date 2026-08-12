package com.ailearning.platform.catalog.api;

import com.ailearning.platform.catalog.domain.CategoryEntity;

import java.util.UUID;

public record CategoryResponse(UUID id, String slug, String name, String description) {
    public static CategoryResponse from(CategoryEntity category) {
        return new CategoryResponse(category.getId(), category.getSlug(), category.getName(), category.getDescription());
    }
}
