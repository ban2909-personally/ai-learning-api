package com.ailearning.platform.notification.adapter.out.messaging.websocket;

import com.ailearning.platform.notification.api.contract.NotificationView;
import com.ailearning.platform.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record RealtimeNotificationMessage(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String targetPath,
        Instant createdAt,
        Instant readAt
) {
    static RealtimeNotificationMessage from(NotificationView view) {
        return new RealtimeNotificationMessage(
                view.id(),
                view.type(),
                view.title(),
                view.body(),
                view.targetPath(),
                view.createdAt(),
                view.readAt()
        );
    }
}
