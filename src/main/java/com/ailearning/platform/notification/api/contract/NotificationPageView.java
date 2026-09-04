package com.ailearning.platform.notification.api.contract;

import java.util.List;
import java.util.UUID;

public record NotificationPageView(
        List<NotificationView> content,
        UUID nextCursor,
        long unreadCount
) {
    public NotificationPageView {
        content = List.copyOf(content);
    }
}
