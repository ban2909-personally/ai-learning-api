package com.ailearning.platform.analytics.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsKafkaConsumerPropertiesTest {
    @Test
    void acceptsOnlyMillisecondOrLongerRetryDelays() {
        assertThat(properties(Duration.ofMillis(1)).hasSafeRetryDelay()).isTrue();
        assertThat(properties(Duration.ZERO).hasSafeRetryDelay()).isFalse();
        assertThat(properties(Duration.ofNanos(1)).hasSafeRetryDelay()).isFalse();
    }

    private AnalyticsKafkaConsumerProperties properties(Duration retryDelay) {
        return new AnalyticsKafkaConsumerProperties(true, "topic", "group", retryDelay);
    }
}
