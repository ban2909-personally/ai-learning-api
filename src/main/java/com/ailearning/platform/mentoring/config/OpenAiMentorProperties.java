package com.ailearning.platform.mentoring.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.ai.openai")
public record OpenAiMentorProperties(
        @NotNull URI baseUrl,
        String apiKey,
        @NotBlank String model,
        @Min(1) int maxOutputTokens,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration callTimeout
) {
}

