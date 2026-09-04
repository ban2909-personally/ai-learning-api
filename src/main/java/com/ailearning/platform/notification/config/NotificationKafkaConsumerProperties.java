package com.ailearning.platform.notification.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.notifications.consumer")
public record NotificationKafkaConsumerProperties(
        boolean enabled,
        @NotBlank String topic,
        @NotBlank String groupId,
        @NotNull Duration retryDelay,
        @Min(1) int maxAttempts,
        @NotBlank String deadLetterTopic
) {
    @AssertTrue(message = "notification Kafka retry delay must be at least one millisecond")
    public boolean hasSafeRetryDelay() {
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            return false;
        }
        try {
            return retryDelay.toMillis() >= 1;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    @AssertTrue(message = "notification dead-letter topic must differ from the source topic")
    public boolean hasDistinctDeadLetterTopic() {
        return topic != null && deadLetterTopic != null && !topic.equals(deadLetterTopic);
    }
}
