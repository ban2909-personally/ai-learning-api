package com.ailearning.platform.commerce.application.port.out;

import com.ailearning.platform.commerce.domain.model.CourseOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseOrderStore {
    Optional<CourseOrder> findByIdempotencyKey(UUID userId, UUID idempotencyKey);

    CourseOrder insertOrGet(CourseOrder order);

    List<CourseOrder> findRecentByUser(UUID userId, int limit);
}
