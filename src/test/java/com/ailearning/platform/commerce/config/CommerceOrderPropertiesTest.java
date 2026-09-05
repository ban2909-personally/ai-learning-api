package com.ailearning.platform.commerce.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceOrderPropertiesTest {
    @Test
    void acceptsOnlyTheOperationalPendingWindow() {
        assertThat(new CommerceOrderProperties(Duration.ofMinutes(5)).hasSafePendingTtl()).isTrue();
        assertThat(new CommerceOrderProperties(Duration.ofHours(24)).hasSafePendingTtl()).isTrue();
        assertThat(new CommerceOrderProperties(Duration.ofMinutes(4)).hasSafePendingTtl()).isFalse();
        assertThat(new CommerceOrderProperties(Duration.ofHours(25)).hasSafePendingTtl()).isFalse();
        assertThat(new CommerceOrderProperties(null).hasSafePendingTtl()).isFalse();
    }
}
