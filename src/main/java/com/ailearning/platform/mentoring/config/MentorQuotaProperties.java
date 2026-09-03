package com.ailearning.platform.mentoring.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.ai.mentor.quota")
public record MentorQuotaProperties(
        @NotBlank String keyPrefix,
        @Min(1) int requests,
        @NotNull Duration window
) {
}

