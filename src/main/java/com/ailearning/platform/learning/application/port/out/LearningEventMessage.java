package com.ailearning.platform.learning.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record LearningEventMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String messageKey,
        String payload,
        Instant occurredAt,
        int attempts
) {
}
