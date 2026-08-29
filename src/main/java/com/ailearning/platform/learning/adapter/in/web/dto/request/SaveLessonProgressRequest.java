package com.ailearning.platform.learning.adapter.in.web.dto.request;

import jakarta.validation.constraints.Min;

public record SaveLessonProgressRequest(@Min(0) int positionSeconds, boolean completed) {}
