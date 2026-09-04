package com.ailearning.platform.notification.adapter.in.web.dto.response;

import com.ailearning.platform.notification.api.contract.NotificationPageView;

import java.util.List;
import java.util.UUID;

public record NotificationPageResponse(
        List<NotificationResponse> content,
        UUID nextCursor,
        long unreadCount
) {
    public static NotificationPageResponse from(NotificationPageView view) {
        return new NotificationPageResponse(
                view.content().stream().map(NotificationResponse::from).toList(),
                view.nextCursor(),
                view.unreadCount()
        );
    }
}
