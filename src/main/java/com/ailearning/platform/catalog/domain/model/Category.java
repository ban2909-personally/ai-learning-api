package com.ailearning.platform.catalog.domain.model;

import java.util.UUID;

public record Category(UUID id, String slug, String name, String description) {
}
