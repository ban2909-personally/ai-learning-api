package com.ailearning.platform.notification.api.contract;

import java.time.Instant;
import java.util.UUID;

public record LessonCompletedNotificationCommand(
        UUID eventId,
        UUID userId,
        Instant occurredAt
) {
}
