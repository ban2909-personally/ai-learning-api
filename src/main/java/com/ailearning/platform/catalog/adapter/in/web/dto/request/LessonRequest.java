package com.ailearning.platform.catalog.adapter.in.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LessonRequest(
        @NotBlank @Size(max = 180) String sectionTitle,
        @NotBlank @Size(max = 180) String title,
        @NotNull @Size(max = 1000) String contentUrl,
        @Min(0) @Max(86400) int durationSeconds,
        boolean preview) {}
