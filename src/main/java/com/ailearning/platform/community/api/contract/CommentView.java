package com.ailearning.platform.community.api.contract;

import java.time.Instant;
import java.util.UUID;

public record CommentView(
        UUID id,
        UUID postId,
        UUID parentId,
        UUID authorId,
        String authorName,
        String body,
        boolean removed,
        Instant createdAt) {}
