package com.ailearning.platform.assessment.api.contract;

import com.ailearning.platform.assessment.domain.enumtype.PracticeSkill;
import com.ailearning.platform.assessment.domain.enumtype.QuestionKind;

import java.util.List;

public record ExamDraftContent(
        String title, String description, int durationMinutes, List<SectionInput> sections) {
    public ExamDraftContent {
        sections = List.copyOf(sections);
    }

    public record SectionInput(
            PracticeSkill skill,
            String title,
            String passage,
            String audioText,
            List<QuestionInput> questions) {
        public SectionInput {
            questions = List.copyOf(questions);
        }
    }

    public record QuestionInput(
            QuestionKind kind,
            String prompt,
            List<String> options,
            String correctAnswer,
            String explanation) {
        public QuestionInput {
            options = List.copyOf(options);
        }
    }
}
