package com.ailearning.platform.identity.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(UUID id, User user, String tokenHash, Instant expiresAt, Instant revokedAt, Instant createdAt) {
    public boolean expiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
