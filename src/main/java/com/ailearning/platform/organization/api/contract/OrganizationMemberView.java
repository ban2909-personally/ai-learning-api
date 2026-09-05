package com.ailearning.platform.organization.api.contract;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMemberView(UUID userId, String role, Instant joinedAt) {
}
