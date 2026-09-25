package com.ailearning.platform.catalog.api.contract;

public record CreateLessonCommand(
        String sectionTitle,
        String title,
        String contentUrl,
        int durationSeconds,
        boolean preview) {}
