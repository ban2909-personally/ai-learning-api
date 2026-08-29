package com.ailearning.platform.identity.domain.model;

import java.util.Set;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String passwordHash,
        String displayName,
        String status,
        Set<String> roles
) {
    public User {
        roles = Set.copyOf(roles);
    }

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
