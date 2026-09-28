package com.ailearning.platform.community.api.contract;

import java.time.Instant;
import java.util.UUID;

public record DirectMessageView(
        UUID id, long sequence, UUID authorId, String body, Instant createdAt) {}
