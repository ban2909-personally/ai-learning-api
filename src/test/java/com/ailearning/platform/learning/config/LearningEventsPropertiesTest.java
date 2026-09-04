package com.ailearning.platform.learning.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LearningEventsPropertiesTest {
    @Test
    void acceptsBoundedDispatcherDurations() {
        var properties = properties(
                Duration.ofSeconds(31),
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                Duration.ofMinutes(5)
        );

        assertThat(validate(properties)).isEmpty();
    }

    @Test
    void rejectsLeaseThatCannotCoverTheWorstCaseBatchTimeout() {
        var properties = properties(
                Duration.ofSeconds(30),
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                Duration.ofMinutes(5)
        );

        assertThat(validate(properties))
                .anySatisfy(violation -> assertThat(violation.getMessage())
                        .contains("claim lease must cover"));
    }

    @Test
    void rejectsNonPositiveOrReversedRetryDurations() {
        var properties = properties(
                Duration.ofSeconds(30),
                Duration.ofSeconds(10),
                Duration.ZERO,
                Duration.ofSeconds(-1)
        );

        assertThat(validate(properties))
                .anySatisfy(violation -> assertThat(violation.getMessage())
                        .contains("durations must be positive"))
                .anySatisfy(violation -> assertThat(violation.getMessage())
                        .contains("initial retry delay must not exceed"));
    }

    private java.util.Set<jakarta.validation.ConstraintViolation<LearningEventsProperties>> validate(
            LearningEventsProperties properties
    ) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(properties);
        }
    }

    private LearningEventsProperties properties(
            Duration claimLease,
            Duration sendTimeout,
            Duration retryInitial,
            Duration retryMax
    ) {
        return new LearningEventsProperties(
                true,
                "lesson-completed",
                2,
                Duration.ofSeconds(1),
                claimLease,
                sendTimeout,
                5_000,
                retryInitial,
                retryMax
        );
    }
}
