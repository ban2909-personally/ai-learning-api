package com.ailearning.platform.flashcard.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CardRequest(
        @NotBlank @Size(max = 2000) String front, @NotBlank @Size(max = 4000) String back) {}
