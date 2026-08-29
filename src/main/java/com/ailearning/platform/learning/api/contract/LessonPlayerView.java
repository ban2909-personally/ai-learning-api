package com.ailearning.platform.learning.api.contract;

import java.util.UUID;

public record LessonPlayerView(UUID courseId, String courseSlug, UUID sectionId, UUID lessonId,
        String title, String contentUrl, int durationSeconds, boolean preview) {}
