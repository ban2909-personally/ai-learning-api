package com.ailearning.platform.mentoring.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.ai.mentor.web")
public record MentorWebProperties(@NotNull Duration streamTimeout) {
}
