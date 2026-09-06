package com.ailearning.platform.organization.api.contract;

import java.time.Instant;
import java.util.UUID;

public record OrganizationView(
        UUID id,
        String slug,
        String name,
        String role,
        Instant createdAt,
        Instant joinedAt
) {
}
