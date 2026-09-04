package com.ailearning.platform.notification.api.usecase;

import com.ailearning.platform.notification.api.contract.LessonCompletedNotificationCommand;
import com.ailearning.platform.notification.api.contract.NotificationPageView;
import com.ailearning.platform.notification.api.contract.NotificationView;

import java.util.Optional;
import java.util.UUID;

public interface NotificationUseCase {
    Optional<NotificationView> projectLessonCompleted(LessonCompletedNotificationCommand command);

    NotificationPageView findMine(UUID userId, UUID before, int limit);

    NotificationView markRead(UUID userId, UUID notificationId);
}
