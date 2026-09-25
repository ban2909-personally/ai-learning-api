package com.ailearning.platform.identity.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateAccount(@NotBlank String role, @NotBlank String status) {}
