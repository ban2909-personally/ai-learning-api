package com.ailearning.platform.notification.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationKafkaConsumerPropertiesTest {
    @Test
    void acceptsABoundedRetryDelay() {
        var properties = new NotificationKafkaConsumerProperties(
                true,
                "lesson-completed",
                "notifications",
                Duration.ofSeconds(10),
                3,
                "lesson-completed.dlt"
        );

        assertThat(properties.hasSafeRetryDelay()).isTrue();
    }

    @Test
    void rejectsZeroOrSubMillisecondRetryToPreventBusyLoops() {
        assertThat(properties(Duration.ZERO).hasSafeRetryDelay()).isFalse();
        assertThat(properties(Duration.ofNanos(1)).hasSafeRetryDelay()).isFalse();
    }

    private NotificationKafkaConsumerProperties properties(Duration retryDelay) {
        return new NotificationKafkaConsumerProperties(
                true,
                "lesson-completed",
                "notifications",
                retryDelay,
                3,
                "lesson-completed.dlt"
        );
    }
}
