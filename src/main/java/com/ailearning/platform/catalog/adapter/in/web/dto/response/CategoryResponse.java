package com.ailearning.platform.catalog.adapter.in.web.dto.response;

import com.ailearning.platform.catalog.domain.model.Category;

import java.util.UUID;

public record CategoryResponse(UUID id, String slug, String name, String description) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.id(), category.slug(), category.name(), category.description());
    }
}
