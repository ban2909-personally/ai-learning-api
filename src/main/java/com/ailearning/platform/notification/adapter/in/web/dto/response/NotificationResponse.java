package com.ailearning.platform.notification.adapter.in.web.dto.response;

import com.ailearning.platform.notification.api.contract.NotificationView;
import com.ailearning.platform.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String targetPath,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationResponse from(NotificationView view) {
        return new NotificationResponse(
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
