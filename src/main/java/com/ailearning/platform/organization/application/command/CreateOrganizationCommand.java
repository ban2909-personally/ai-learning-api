package com.ailearning.platform.organization.application.command;

import java.util.Objects;
import java.util.UUID;

public record CreateOrganizationCommand(
        UUID creatorId,
        String name,
        String slug,
        UUID idempotencyKey
) {
    public CreateOrganizationCommand {
        Objects.requireNonNull(creatorId, "creatorId is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(slug, "slug is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
    }
}
