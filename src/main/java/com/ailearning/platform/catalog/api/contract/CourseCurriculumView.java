package com.ailearning.platform.catalog.api.contract;

import java.util.List;
import java.util.UUID;

public record CourseCurriculumView(UUID courseId, String courseSlug, String courseTitle, List<Section> sections) {
    public record Section(UUID id, String title, int order, List<Lesson> lessons) {}
    public record Lesson(UUID id, String title, int durationSeconds, boolean preview, String contentUrl, int order) {}
}
