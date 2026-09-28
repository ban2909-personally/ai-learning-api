package com.ailearning.platform.assessment.domain.model;

import java.util.List;

public record PracticeExamSummary(
        String slug, String title, String description, int durationMinutes, List<String> skills) {
    public PracticeExamSummary {
        skills = List.copyOf(skills);
    }
}
