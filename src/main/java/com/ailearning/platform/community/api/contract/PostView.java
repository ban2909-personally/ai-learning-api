package com.ailearning.platform.community.api.contract;

import java.time.Instant;
import java.util.UUID;

public record PostView(
        UUID id,
        UUID authorId,
        String authorName,
        UUID spaceId,
        String spaceName,
        String body,
        UUID sharedPostId,
        String sharedBody,
        String sharedAuthorName,
        Instant createdAt,
        long likeCount,
        long commentCount,
        long shareCount,
        boolean likedByViewer,
        boolean shareable) {}
