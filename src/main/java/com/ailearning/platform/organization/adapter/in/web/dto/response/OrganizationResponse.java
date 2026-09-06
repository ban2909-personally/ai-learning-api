package com.ailearning.platform.organization.adapter.in.web.dto.response;

import com.ailearning.platform.organization.api.contract.OrganizationView;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String slug,
        String name,
        String role,
        Instant createdAt,
        Instant joinedAt
) {
    public static OrganizationResponse from(OrganizationView organization) {
        return new OrganizationResponse(
                organization.id(),
                organization.slug(),
                organization.name(),
                organization.role(),
                organization.createdAt(),
                organization.joinedAt()
        );
    }
}
