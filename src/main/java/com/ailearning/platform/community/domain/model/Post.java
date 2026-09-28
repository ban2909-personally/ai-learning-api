package com.ailearning.platform.community.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Post(
        UUID id,
        UUID authorId,
        UUID spaceId,
        UUID sharedPostId,
        String body,
        UUID mediaId,
        PostStatus status,
        Instant createdAt) {
    public boolean active() {
        return status == PostStatus.ACTIVE;
    }
}
