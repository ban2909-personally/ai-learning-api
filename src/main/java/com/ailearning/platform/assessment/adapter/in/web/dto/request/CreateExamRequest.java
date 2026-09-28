package com.ailearning.platform.assessment.adapter.in.web.dto.request;

import jakarta.validation.constraints.*;

public record CreateExamRequest(
        @NotBlank @Size(max = 120) String slug,
        @NotBlank @Size(max = 180) String title,
        @NotNull @Size(max = 1000) String description,
        @Min(1) @Max(240) int durationMinutes) {}
