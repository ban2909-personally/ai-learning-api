package com.ailearning.platform.organization.domain.model;

import com.ailearning.platform.organization.domain.valueobject.OrganizationSlug;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Organization(
        UUID id,
        OrganizationSlug slug,
        String name,
        UUID createdBy,
        UUID idempotencyKey,
        Instant createdAt
) {
    private static final int MAX_NAME_LENGTH = 120;

    public Organization {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(slug, "slug is required");
        Objects.requireNonNull(name, "name is required");
        name = name.trim();
        if (name.length() < 2 || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("organization name must contain between 2 and 120 characters");
        }
        Objects.requireNonNull(createdBy, "createdBy is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }
}
