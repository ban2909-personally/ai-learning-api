package com.ailearning.platform.commerce.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.commerce.orders")
public record CommerceOrderProperties(@NotNull Duration pendingTtl) {
    private static final Duration MINIMUM_PENDING_TTL = Duration.ofMinutes(5);
    private static final Duration MAXIMUM_PENDING_TTL = Duration.ofHours(24);

    @AssertTrue(message = "commerce order pending TTL must be between 5 minutes and 24 hours")
    public boolean hasSafePendingTtl() {
        return pendingTtl != null
                && pendingTtl.compareTo(MINIMUM_PENDING_TTL) >= 0
                && pendingTtl.compareTo(MAXIMUM_PENDING_TTL) <= 0;
    }
}
