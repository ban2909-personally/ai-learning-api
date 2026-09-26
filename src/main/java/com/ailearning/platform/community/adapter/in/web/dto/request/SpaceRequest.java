package com.ailearning.platform.community.adapter.in.web.dto.request;

import com.ailearning.platform.community.domain.model.SpaceKind;
import com.ailearning.platform.community.domain.model.SpaceVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SpaceRequest(
        @NotBlank @Size(min = 3, max = 120) String name,
        @NotNull String description,
        @NotNull SpaceKind kind,
        @NotNull SpaceVisibility visibility) {}
