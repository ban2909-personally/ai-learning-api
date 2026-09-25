package com.ailearning.platform.catalog.domain.model;

import java.util.UUID;

public record ManagedLesson(
        UUID id,
        String sectionTitle,
        String title,
        String contentUrl,
        int durationSeconds,
        boolean preview) {}
