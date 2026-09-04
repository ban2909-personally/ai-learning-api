package com.ailearning.platform.notification.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.notifications.consumer")
public record NotificationKafkaConsumerProperties(
        boolean enabled,
        @NotBlank String topic,
        @NotBlank String groupId,
        @NotNull Duration retryDelay
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
}
