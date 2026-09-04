package com.ailearning.platform.notification.api.contract;

import com.ailearning.platform.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationView(
        UUID id,
        UUID recipientId,
        NotificationType type,
        String title,
        String body,
        String targetPath,
        Instant createdAt,
        Instant readAt
) {
}
