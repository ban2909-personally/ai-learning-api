package com.ailearning.platform.identity.application.port.out;

import com.ailearning.platform.identity.domain.model.RefreshSession;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionStore {
    Optional<RefreshSession> findActiveForUpdate(String tokenHash);
    void create(UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant createdAt);
    void revoke(UUID id, Instant revokedAt);
}
