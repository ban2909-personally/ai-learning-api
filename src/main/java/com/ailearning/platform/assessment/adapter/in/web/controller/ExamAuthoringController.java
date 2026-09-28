package com.ailearning.platform.assessment.adapter.in.web.controller;

import com.ailearning.platform.assessment.adapter.in.web.dto.request.CreateExamRequest;
import com.ailearning.platform.assessment.adapter.in.web.dto.request.ExamVersionRequest;
import com.ailearning.platform.assessment.adapter.in.web.dto.request.SaveExamRequest;
import com.ailearning.platform.assessment.api.contract.ExamDraftContent;
import com.ailearning.platform.assessment.api.usecase.ExamAuthoringUseCase;
import com.ailearning.platform.assessment.domain.model.ExamRevision;
import com.ailearning.platform.assessment.domain.model.ExamRevisionSummary;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/practice/authoring/exams")
public class ExamAuthoringController {
    private final ExamAuthoringUseCase authoring;

    public ExamAuthoringController(ExamAuthoringUseCase authoring) {
        this.authoring = authoring;
    }

    @GetMapping
    List<ExamRevisionSummary> list(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
        return authoring.list(actor(jwt), page);
    }

    @GetMapping("/{id}")
    ExamRevision workspace(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return authoring.workspace(actor(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ExamRevision create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateExamRequest request) {
        return authoring.create(
                actor(jwt),
                request.slug(),
                request.title(),
                request.description(),
                request.durationMinutes());
    }

    @PutMapping("/{id}")
    ExamRevision save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody SaveExamRequest request) {
        ExamDraftContent content =
                new ExamDraftContent(
                        request.title(),
                        request.description(),
                        request.durationMinutes(),
                        request.sections().stream().map(this::section).toList());
        return authoring.save(actor(jwt), id, request.expectedVersion(), content);
    }

    @PostMapping("/{id}/submit")
    ExamRevision submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ExamVersionRequest request) {
        return authoring.submit(actor(jwt), id, request.expectedVersion());
    }

    @PostMapping("/{id}/withdraw")
    ExamRevision withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ExamVersionRequest request) {
        return authoring.withdraw(actor(jwt), id, request.expectedVersion());
    }

    @PostMapping("/{id}/publish")
    ExamRevision publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ExamVersionRequest request) {
        return authoring.publish(actor(jwt), id, request.expectedVersion());
    }

    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    ExamRevision clonePublished(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ExamVersionRequest request) {
        return authoring.clonePublished(actor(jwt), id, request.expectedVersion());
    }

    private ExamDraftContent.SectionInput section(SaveExamRequest.SectionRequest request) {
        return new ExamDraftContent.SectionInput(
                request.skill(),
                request.title(),
                request.passage(),
                request.audioText(),
                request.questions().stream().map(this::question).toList());
    }

    private ExamDraftContent.QuestionInput question(SaveExamRequest.QuestionRequest request) {
        return new ExamDraftContent.QuestionInput(
                request.kind(),
                request.prompt(),
                request.options(),
                request.correctAnswer(),
                request.explanation());
    }

    private static UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
