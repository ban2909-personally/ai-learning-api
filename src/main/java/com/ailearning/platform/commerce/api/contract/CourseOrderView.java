package com.ailearning.platform.commerce.api.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseOrderView(
        UUID id,
        UUID courseId,
        String courseSlug,
        String courseTitle,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        Instant expiresAt
) {
}
