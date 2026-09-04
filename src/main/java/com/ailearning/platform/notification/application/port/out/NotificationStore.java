package com.ailearning.platform.notification.application.port.out;

import com.ailearning.platform.notification.domain.model.Notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationStore {
    boolean create(Notification notification);

    List<Notification> findByRecipient(UUID recipientId, UUID before, int fetchSize);

    long countUnread(UUID recipientId);

    Optional<Notification> markRead(UUID recipientId, UUID notificationId, Instant readAt);
}
