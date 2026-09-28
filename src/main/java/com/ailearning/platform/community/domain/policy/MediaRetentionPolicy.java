package com.ailearning.platform.community.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class MediaRetentionPolicy {
    public Instant expiresAt(Instant uploadedAt) {
        return uploadedAt.plus(Duration.ofDays(14));
    }

    public boolean expired(Instant expiresAt, Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void requireOwnedObject(UUID id, String key) {
        if (!("community/" + id).equals(key))
            throw new IllegalArgumentException("Not a community-owned media object");
    }
}
