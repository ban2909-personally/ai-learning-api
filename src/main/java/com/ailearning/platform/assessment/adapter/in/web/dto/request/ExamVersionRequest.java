package com.ailearning.platform.assessment.adapter.in.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExamVersionRequest(@NotNull @Min(0) Long expectedVersion) {}
