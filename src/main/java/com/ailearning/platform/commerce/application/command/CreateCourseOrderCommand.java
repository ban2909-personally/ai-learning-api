package com.ailearning.platform.commerce.application.command;

import java.util.Objects;
import java.util.UUID;

public record CreateCourseOrderCommand(UUID userId, String courseSlug, UUID idempotencyKey) {
    public CreateCourseOrderCommand {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(courseSlug, "courseSlug is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        courseSlug = courseSlug.trim();
        if (courseSlug.isEmpty() || courseSlug.length() > 160) {
            throw new IllegalArgumentException("courseSlug must contain between 1 and 160 characters");
        }
    }
}
