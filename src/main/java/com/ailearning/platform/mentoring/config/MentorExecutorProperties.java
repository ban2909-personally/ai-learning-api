package com.ailearning.platform.mentoring.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ai.mentor.executor")
public record MentorExecutorProperties(
        @Min(1) @Max(256) int corePoolSize,
        @Min(1) @Max(256) int maxPoolSize,
        @Min(0) @Max(10_000) int queueCapacity
) {
    @AssertTrue(message = "mentor executor core pool size must not exceed maximum pool size")
    public boolean hasOrderedPoolSizes() {
        return corePoolSize <= maxPoolSize;
    }
}
