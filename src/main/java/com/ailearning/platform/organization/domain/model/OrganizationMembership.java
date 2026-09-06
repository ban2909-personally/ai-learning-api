package com.ailearning.platform.organization.domain.model;

import com.ailearning.platform.organization.domain.enums.OrganizationRole;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrganizationMembership(
        UUID id,
        UUID organizationId,
        UUID userId,
        OrganizationRole role,
        Instant joinedAt
) {
    public OrganizationMembership {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(joinedAt, "joinedAt is required");
    }
}
