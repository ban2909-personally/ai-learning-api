package com.ailearning.platform.learning.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.messaging.learning-events")
public record LearningEventsProperties(
        boolean enabled,
        @NotBlank String topic,
        @Min(1) int batchSize,
        @NotNull Duration pollDelay,
        @NotNull Duration claimLease,
        @NotNull Duration sendTimeout,
        @Min(1) long producerMaxBlockMs,
        @NotNull Duration retryInitial,
        @NotNull Duration retryMax
) {
    @AssertTrue(message = "learning-event durations must be positive")
    public boolean hasPositiveDurations() {
        return isPositive(pollDelay)
                && isPositive(claimLease)
                && isPositive(sendTimeout)
                && isPositive(retryInitial)
                && isPositive(retryMax);
    }

    @AssertTrue(message = "claim lease must cover the worst-case Kafka publication time for one batch")
    public boolean hasSafeClaimLease() {
        if (claimLease == null || sendTimeout == null || producerMaxBlockMs < 1 || batchSize < 1) {
            return false;
        }
        try {
            Duration publicationBudget = sendTimeout.plusMillis(producerMaxBlockMs);
            Duration worstCaseBatchTime = publicationBudget.multipliedBy(batchSize);
            return claimLease.compareTo(worstCaseBatchTime) > 0;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    @AssertTrue(message = "initial retry delay must not exceed maximum retry delay")
    public boolean hasOrderedRetryDelays() {
        return retryInitial != null
                && retryMax != null
                && retryInitial.compareTo(retryMax) <= 0;
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
