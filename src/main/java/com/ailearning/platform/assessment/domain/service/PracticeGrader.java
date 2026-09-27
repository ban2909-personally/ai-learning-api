package com.ailearning.platform.assessment.domain.service;

import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PracticeGrader {
    private PracticeGrader() {}

    public static Result grade(PracticeExam exam, PracticeAttempt attempt) {
        List<SectionResult> sections = exam.sections().stream()
                .map(section -> {
                    List<QuestionResult> questions = section.questions().stream()
                            .map(question -> gradeQuestion(question, attempt))
                            .toList();
                    return new SectionResult(section.skill(), section.title(),
                            (int) questions.stream().filter(q -> Boolean.TRUE.equals(q.correct())).count(),
                            (int) questions.stream().filter(q -> q.correct() != null).count(),
                            questions);
                }).toList();
        return new Result(attempt.id(), exam.title(),
                sections.stream().mapToInt(SectionResult::correct).sum(),
                sections.stream().mapToInt(SectionResult::total).sum(), sections);
    }

    private static QuestionResult gradeQuestion(
            PracticeExam.Question question, PracticeAttempt attempt) {
        String answer = attempt.answers().getOrDefault(question.id(), "");
        if ("WRITING".equals(question.kind())) {
            return new QuestionResult(question.id(), answer, null, null, null,
                    answer.isBlank() ? "UNANSWERED" : "PENDING_REVIEW");
        }
        boolean correct = normalize(answer).equals(normalize(question.correctAnswer()))
                && !answer.isBlank();
        return new QuestionResult(question.id(), answer, correct,
                question.correctAnswer(), question.explanation(), "GRADED");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    public record Result(UUID attemptId, String examTitle, int correct, int total,
                         List<SectionResult> sections) {}

    public record SectionResult(String skill, String title, int correct, int total,
                                List<QuestionResult> questions) {}

    public record QuestionResult(UUID questionId, String answer, Boolean correct,
                                 String correctAnswer, String explanation, String status) {}
}
