package com.ailearning.platform.learning.adapter.in.web.dto.response;

import com.ailearning.platform.learning.api.contract.LessonProgressView;
import java.time.Instant;
import java.util.UUID;

public record LessonProgressResponse(UUID lessonId, int positionSeconds, boolean completed, Instant updatedAt) {
    public static LessonProgressResponse from(LessonProgressView value) {
        return new LessonProgressResponse(value.lessonId(), value.positionSeconds(), value.completed(), value.updatedAt());
    }
}
