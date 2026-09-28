package com.ailearning.platform.community.domain.policy;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class MediaRetentionPolicyTest {
    @Test
    void expiresExactlyAtFourteenDaysAndOnlyOwnsExactCommunityKeys() {
        var policy = new MediaRetentionPolicy();
        var upload = Instant.parse("2026-09-01T00:00:00Z");
        var expiry = policy.expiresAt(upload);
        assertThat(expiry).isEqualTo(Instant.parse("2026-09-15T00:00:00Z"));
        assertThat(policy.expired(expiry, expiry.minusNanos(1))).isFalse();
        assertThat(policy.expired(expiry, expiry)).isTrue();
        var id = UUID.randomUUID();
        policy.requireOwnedObject(id, "community/" + id);
        assertThatThrownBy(() -> policy.requireOwnedObject(id, "lessons/" + id))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
