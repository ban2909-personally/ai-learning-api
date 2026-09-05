package com.ailearning.platform.commerce.domain.model;

import com.ailearning.platform.commerce.domain.enums.CourseOrderStatus;
import com.ailearning.platform.commerce.domain.valueobject.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CourseOrder(
        UUID id,
        UUID userId,
        UUID courseId,
        String courseSlug,
        String courseTitle,
        Money total,
        UUID idempotencyKey,
        Instant createdAt,
        Instant expiresAt
) {
    public CourseOrder {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(courseId, "courseId is required");
        courseSlug = requireText(courseSlug, "courseSlug", 160);
        courseTitle = requireText(courseTitle, "courseTitle", 180);
        Objects.requireNonNull(total, "total is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public CourseOrderStatus statusAt(Instant now) {
        Objects.requireNonNull(now, "now is required");
        return now.isBefore(expiresAt) ? CourseOrderStatus.PENDING_PAYMENT : CourseOrderStatus.EXPIRED;
    }

    private static String requireText(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain between 1 and " + maxLength + " characters");
        }
        return normalized;
    }
}
