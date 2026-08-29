package com.ailearning.platform.learning.api.contract;

import java.time.Instant;
import java.util.UUID;

public record LessonProgressView(UUID lessonId, int positionSeconds, boolean completed, Instant updatedAt) {}
