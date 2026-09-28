package com.ailearning.platform.community.api.contract;

import java.time.Instant;
import java.util.UUID;

public record SpaceMessageView(
        UUID id,
        long sequence,
        UUID authorId,
        String authorName,
        String body,
        boolean removed,
        Instant createdAt) {}
