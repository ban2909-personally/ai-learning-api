package com.ailearning.platform.assessment.adapter.in.web.controller;

import com.ailearning.platform.assessment.api.usecase.PracticeUseCase;
import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.assessment.domain.model.WritingReview;
import com.ailearning.platform.assessment.domain.model.WritingSubmission;
import com.ailearning.platform.assessment.domain.service.PracticeGrader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/practice")
public class PracticeController {
    private final PracticeUseCase practice;

    public PracticeController(PracticeUseCase practice) {
        this.practice = practice;
    }

    @GetMapping("/exams")
    List<ExamSummary> exams() {
        return practice.list().stream().map(exam -> new ExamSummary(
                exam.slug(), exam.title(), exam.description(), exam.durationMinutes(),
                exam.sections().stream().map(PracticeExam.Section::skill).toList())).toList();
    }

    @GetMapping("/exams/{slug}")
    ExamView exam(@PathVariable String slug) {
        return view(practice.exam(slug));
    }

    @PostMapping("/exams/{slug}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    PracticeAttempt start(@AuthenticationPrincipal Jwt jwt, @PathVariable String slug) {
        return practice.start(actor(jwt), slug);
    }

    @GetMapping("/attempts/{id}")
    AttemptView attempt(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        PracticeAttempt attempt = practice.attempt(actor(jwt), id);
        PracticeExam exam = practice.exam(attempt.examId());
        return new AttemptView(attempt.id(), attempt.status(), attempt.answers(), view(exam));
    }

    @PutMapping("/attempts/{id}/answers/{questionId}")
    PracticeAttempt answer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                           @PathVariable UUID questionId, @Valid @RequestBody AnswerRequest request) {
        return practice.answer(actor(jwt), id, questionId, request.answer());
    }

    @PostMapping("/attempts/{id}/submit")
    PracticeGrader.Result submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return practice.submit(actor(jwt), id);
    }

    @GetMapping("/attempts/{id}/result")
    PracticeGrader.Result result(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return practice.result(actor(jwt), id);
    }

    @GetMapping("/reviews/pending")
    List<WritingSubmission> pendingWriting(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
        return practice.pendingWriting(actor(jwt), page);
    }

    @PutMapping("/attempts/{id}/writing/{questionId}/review")
    WritingReview reviewWriting(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @PathVariable UUID questionId,
                                @Valid @RequestBody WritingReviewRequest request) {
        return practice.reviewWriting(actor(jwt), id, questionId,
                request.taskScore(), request.coherenceScore(), request.vocabularyScore(),
                request.grammarScore(), request.feedback());
    }

    private static UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static ExamView view(PracticeExam exam) {
        return new ExamView(exam.slug(), exam.title(), exam.description(),
                exam.durationMinutes(), exam.sections().stream().map(section ->
                        new SectionView(section.id(), section.skill(), section.title(),
                                section.passage(), section.audioText(), section.questions().stream()
                                .map(question -> new QuestionView(question.id(), question.kind(),
                                        question.prompt(), question.options())).toList())).toList());
    }

    record ExamSummary(String slug, String title, String description,
                       int durationMinutes, List<String> skills) {}

    record ExamView(String slug, String title, String description,
                    int durationMinutes, List<SectionView> sections) {}

    record SectionView(UUID id, String skill, String title, String passage,
                       String audioText, List<QuestionView> questions) {}

    record QuestionView(UUID id, String kind, String prompt, List<String> options) {}

    record AttemptView(UUID id, String status, Map<UUID, String> answers, ExamView exam) {}

    record AnswerRequest(@NotNull String answer) {}

    record WritingReviewRequest(@NotNull @Min(0) @Max(5) Integer taskScore,
                                @NotNull @Min(0) @Max(5) Integer coherenceScore,
                                @NotNull @Min(0) @Max(5) Integer vocabularyScore,
                                @NotNull @Min(0) @Max(5) Integer grammarScore,
                                @NotBlank @Size(max = 2000) String feedback) {}
}
