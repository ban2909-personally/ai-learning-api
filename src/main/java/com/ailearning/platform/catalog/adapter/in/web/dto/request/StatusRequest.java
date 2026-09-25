package com.ailearning.platform.catalog.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record StatusRequest(@NotBlank String status) {}
