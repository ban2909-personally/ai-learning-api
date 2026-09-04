package com.ailearning.platform.learning.application.port.out;

import com.ailearning.platform.learning.domain.event.LessonCompleted;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LearningEventOutbox {
    void append(LessonCompleted event);

    List<LearningEventMessage> claimAvailable(
            String owner,
            int limit,
            Instant now,
            Instant lockedUntil
    );

    void markPublished(UUID eventId, String owner, Instant publishedAt);

    void reschedule(UUID eventId, String owner, Instant availableAt, String failureCode);
}
