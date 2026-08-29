package com.ailearning.platform.catalog.api.contract;

import java.util.UUID;

public record LessonContentView(UUID courseId, String courseSlug, UUID sectionId, UUID lessonId,
        String title, String contentUrl, int durationSeconds, boolean preview) {}
