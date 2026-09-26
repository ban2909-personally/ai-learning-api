package com.ailearning.platform.community.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Post(
        UUID id,
        UUID authorId,
        UUID spaceId,
        UUID sharedPostId,
        String body,
        boolean active,
        Instant createdAt) {}
