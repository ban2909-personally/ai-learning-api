package com.ailearning.platform.commerce.domain.model;

import com.ailearning.platform.commerce.domain.enums.CourseOrderStatus;
import com.ailearning.platform.commerce.domain.valueobject.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CourseOrderTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-05T00:00:00Z");

    @Test
    void derivesPendingAndExpiredStatusAtTheExactBoundary() {
        CourseOrder order = order(CREATED_AT.plusSeconds(60));

        assertThat(order.statusAt(CREATED_AT.plusSeconds(59))).isEqualTo(CourseOrderStatus.PENDING_PAYMENT);
        assertThat(order.statusAt(CREATED_AT.plusSeconds(60))).isEqualTo(CourseOrderStatus.EXPIRED);
    }

    @Test
    void rejectsAnExpiryThatDoesNotFollowCreation() {
        assertThatIllegalArgumentException().isThrownBy(() -> order(CREATED_AT));
    }

    private CourseOrder order(Instant expiresAt) {
        return new CourseOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "spring-boot",
                "Spring Boot Production",
                new Money(new BigDecimal("250000"), "VND"),
                UUID.randomUUID(),
                CREATED_AT,
                expiresAt
        );
    }
}
