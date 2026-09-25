package com.ailearning.platform.identity.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ManagedAccount(
        UUID id,
        String email,
        String displayName,
        String status,
        Set<String> roles,
        Instant createdAt) {
    public ManagedAccount {
        roles = Set.copyOf(roles);
    }
}
