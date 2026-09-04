package com.ailearning.platform.analytics.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CompletionFactTest {
    @Test
    void rejectsAnIncompleteFact() {
        assertThatNullPointerException().isThrownBy(() -> new CompletionFact(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                Instant.now()
        )).withMessage("userId is required");
    }
}
