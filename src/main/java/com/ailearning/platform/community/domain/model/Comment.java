package com.ailearning.platform.community.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Comment(
        UUID id,
        UUID postId,
        UUID parentId,
        UUID authorId,
        String body,
        boolean active,
        Instant createdAt) {}
