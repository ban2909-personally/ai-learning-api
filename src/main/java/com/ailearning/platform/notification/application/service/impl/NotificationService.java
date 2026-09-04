package com.ailearning.platform.notification.application.service.impl;

import com.ailearning.platform.notification.api.contract.LessonCompletedNotificationCommand;
import com.ailearning.platform.notification.api.contract.NotificationPageView;
import com.ailearning.platform.notification.api.contract.NotificationView;
import com.ailearning.platform.notification.api.usecase.NotificationUseCase;
import com.ailearning.platform.notification.application.port.out.NotificationRealtimeDelivery;
import com.ailearning.platform.notification.application.port.out.NotificationStore;
import com.ailearning.platform.notification.domain.model.Notification;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class NotificationService implements NotificationUseCase {
    static final int MAX_PAGE_SIZE = 50;

    private final NotificationStore notifications;
    private final NotificationRealtimeDelivery realtime;
    private final Clock clock;

    public NotificationService(
            NotificationStore notifications,
            NotificationRealtimeDelivery realtime,
            Clock clock
    ) {
        this.notifications = notifications;
        this.realtime = realtime;
        this.clock = clock;
    }

    @Override
    public Optional<NotificationView> projectLessonCompleted(LessonCompletedNotificationCommand command) {
        Notification notification = Notification.lessonCompleted(
                command.eventId(),
                command.userId(),
                command.occurredAt()
        );
        if (!notifications.create(notification)) {
            return Optional.empty();
        }

        NotificationView view = toView(notification);
        realtime.publish(view);
        return Optional.of(view);
    }

    @Override
    public NotificationPageView findMine(UUID userId, UUID before, int limit) {
        requireValidLimit(limit);
        List<Notification> fetched = notifications.findByRecipient(userId, before, limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<NotificationView> content = fetched.stream()
                .limit(limit)
                .map(this::toView)
                .toList();
        UUID nextCursor = hasMore ? content.getLast().id() : null;
        return new NotificationPageView(content, nextCursor, notifications.countUnread(userId));
    }

    @Override
    public NotificationView markRead(UUID userId, UUID notificationId) {
        Notification notification = notifications.markRead(userId, notificationId, clock.instant())
                .orElseThrow(() -> new BusinessException(
                        "notification_not_found",
                        ErrorType.NOT_FOUND,
                        "Không tìm thấy thông báo."
                ));
        return toView(notification);
    }

    private void requireValidLimit(int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new BusinessException(
                    "invalid_notification_page_size",
                    ErrorType.BAD_REQUEST,
                    "Số thông báo mỗi trang phải từ 1 đến 50."
            );
        }
    }

    private NotificationView toView(Notification notification) {
        return new NotificationView(
                notification.id(),
                notification.recipientId(),
                notification.type(),
                notification.title(),
                notification.body(),
                notification.targetPath(),
                notification.createdAt(),
                notification.readAt()
        );
    }
}
