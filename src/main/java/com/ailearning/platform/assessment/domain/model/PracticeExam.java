package com.ailearning.platform.assessment.domain.model;

import java.util.List;
import java.util.UUID;

public record PracticeExam(
        UUID id,
        String slug,
        String title,
        String description,
        int durationMinutes,
        List<Section> sections) {
    public PracticeExam {
        sections = List.copyOf(sections);
    }

    public record Section(
            UUID id,
            String skill,
            String title,
            String passage,
            String audioText,
            List<Question> questions) {
        public Section {
            questions = List.copyOf(questions);
        }
    }

    public record Question(
            UUID id,
            String kind,
            String prompt,
            List<String> options,
            String correctAnswer,
            String explanation) {
        public Question {
            options = List.copyOf(options);
        }
    }
}
