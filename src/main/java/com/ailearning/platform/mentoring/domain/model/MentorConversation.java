package com.ailearning.platform.mentoring.domain.model;

import java.time.Instant;
import java.util.UUID;

public record MentorConversation(
        UUID id,
        UUID userId,
        UUID courseId,
        UUID lessonId,
        Instant createdAt,
        Instant updatedAt
) {
}

