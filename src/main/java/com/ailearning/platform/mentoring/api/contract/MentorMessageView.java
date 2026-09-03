package com.ailearning.platform.mentoring.api.contract;

import com.ailearning.platform.mentoring.domain.enums.MentorMessageRole;

import java.time.Instant;
import java.util.UUID;

public record MentorMessageView(
        UUID id,
        MentorMessageRole role,
        String content,
        Instant createdAt
) {
}

