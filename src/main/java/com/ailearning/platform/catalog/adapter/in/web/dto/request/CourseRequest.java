package com.ailearning.platform.catalog.adapter.in.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 160) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 320) String shortDescription,
        @NotBlank @Size(max = 20000) String description,
        @NotBlank @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED") String level,
        @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") BigDecimal price) {}
