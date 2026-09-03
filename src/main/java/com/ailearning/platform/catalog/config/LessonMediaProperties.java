package com.ailearning.platform.catalog.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "app.media")
public record LessonMediaProperties(
        @NotNull DataSize maxUploadSize,
        @NotEmpty Set<String> allowedContentTypes
) {
}
