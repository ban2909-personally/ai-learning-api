package com.ailearning.platform.mentoring.domain.model;

import com.ailearning.platform.mentoring.domain.enums.MentorMessageRole;

import java.time.Instant;
import java.util.UUID;

public record MentorMessage(
        UUID id,
        UUID conversationId,
        MentorMessageRole role,
        String content,
        String providerModel,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt
) {
}

