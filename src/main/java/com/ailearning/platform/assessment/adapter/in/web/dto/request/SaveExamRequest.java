package com.ailearning.platform.assessment.adapter.in.web.dto.request;

import com.ailearning.platform.assessment.domain.enumtype.PracticeSkill;
import com.ailearning.platform.assessment.domain.enumtype.QuestionKind;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record SaveExamRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(max = 180) String title,
        @NotNull @Size(max = 1000) String description,
        @Min(1) @Max(240) int durationMinutes,
        @NotNull @Size(max = 20) List<@NotNull @Valid SectionRequest> sections) {
    public record SectionRequest(
            @NotNull PracticeSkill skill,
            @NotNull @Size(max = 180) String title,
            @Size(max = 20000) String passage,
            @Size(max = 20000) String audioText,
            @NotNull @Size(max = 250) List<@NotNull @Valid QuestionRequest> questions) {}

    public record QuestionRequest(
            @NotNull QuestionKind kind,
            @NotNull @Size(max = 10000) String prompt,
            @NotNull @Size(max = 10) List<@NotNull @Size(max = 500) String> options,
            @Size(max = 500) String correctAnswer,
            @NotNull @Size(max = 5000) String explanation) {}
}
