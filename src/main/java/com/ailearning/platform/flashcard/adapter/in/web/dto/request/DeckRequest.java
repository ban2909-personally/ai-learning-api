package com.ailearning.platform.flashcard.adapter.in.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DeckRequest(
        @NotBlank @Size(max = 180) String title,
        @NotNull @Size(max = 1000) String description,
        @NotEmpty @Size(max = 200) List<@Valid CardRequest> cards) {}
