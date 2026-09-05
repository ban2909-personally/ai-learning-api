package com.ailearning.platform.commerce.adapter.in.web.dto.response;

import com.ailearning.platform.commerce.api.contract.CourseOrderView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseOrderResponse(
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
    public static CourseOrderResponse from(CourseOrderView order) {
        return new CourseOrderResponse(
                order.id(),
                order.courseId(),
                order.courseSlug(),
                order.courseTitle(),
                order.amount(),
                order.currency(),
                order.status(),
                order.createdAt(),
                order.expiresAt()
        );
    }
}
