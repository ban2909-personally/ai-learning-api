package com.ailearning.platform.notification.domain.model;

import com.ailearning.platform.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID recipientId,
        NotificationType type,
        String title,
        String body,
        String targetPath,
        Instant createdAt,
        Instant readAt
) {
    private static final String COMPLETION_TITLE = "Hoàn thành bài học";
    private static final String COMPLETION_BODY =
            "Bạn đã hoàn thành một bài học. Hãy tiếp tục giữ nhịp học nhé!";
    private static final String LEARNING_PATH = "/my-learning";

    public Notification {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(recipientId, "recipientId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireText(title, "title");
        requireText(body, "body");
        if (targetPath == null || !targetPath.startsWith("/")) {
            throw new IllegalArgumentException("targetPath must be an application-relative path");
        }
        if (readAt != null && readAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("readAt must not precede createdAt");
        }
    }

    public static Notification lessonCompleted(UUID eventId, UUID recipientId, Instant occurredAt) {
        return new Notification(
                eventId,
                recipientId,
                NotificationType.LESSON_COMPLETED,
                COMPLETION_TITLE,
                COMPLETION_BODY,
                LEARNING_PATH,
                occurredAt,
                null
        );
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
