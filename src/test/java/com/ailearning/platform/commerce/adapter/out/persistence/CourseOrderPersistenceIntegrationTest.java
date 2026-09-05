package com.ailearning.platform.commerce.adapter.out.persistence;

import com.ailearning.platform.commerce.application.port.out.CourseOrderStore;
import com.ailearning.platform.commerce.domain.model.CourseOrder;
import com.ailearning.platform.commerce.domain.valueobject.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CourseOrderPersistenceIntegrationTest {
    private static final UUID FIRST_USER = UUID.randomUUID();
    private static final UUID SECOND_USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-05T08:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_commerce_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    CourseOrderStore orders;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM course_orders");
    }

    @Test
    void storesAnImmutableSnapshotAndReturnsRecentOrdersForOnlyTheRequestedUser() {
        CourseOrder older = order(UUID.randomUUID(), FIRST_USER, UUID.randomUUID(), NOW.minusSeconds(10));
        CourseOrder newer = order(UUID.randomUUID(), FIRST_USER, UUID.randomUUID(), NOW);
        CourseOrder anotherUsers = order(UUID.randomUUID(), SECOND_USER, UUID.randomUUID(), NOW.plusSeconds(10));

        orders.insertOrGet(older);
        orders.insertOrGet(newer);
        orders.insertOrGet(anotherUsers);

        assertThat(orders.findRecentByUser(FIRST_USER, 1))
                .singleElement()
                .isEqualTo(newer);
        assertThat(orders.findByIdempotencyKey(FIRST_USER, older.idempotencyKey()))
                .contains(older);
        assertThat(orders.findByIdempotencyKey(SECOND_USER, older.idempotencyKey()))
                .isEmpty();
    }

    @Test
    void scopesTheSameIdempotencyKeyByUser() {
        UUID key = UUID.randomUUID();
        CourseOrder first = order(UUID.randomUUID(), FIRST_USER, key, NOW);
        CourseOrder second = order(UUID.randomUUID(), SECOND_USER, key, NOW);

        assertThat(orders.insertOrGet(first)).isEqualTo(first);
        assertThat(orders.insertOrGet(second)).isEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_orders", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentRetriesWithTheSameKeyReturnTheSinglePersistedOrder() throws Exception {
        UUID key = UUID.randomUUID();
        CourseOrder first = order(UUID.randomUUID(), FIRST_USER, key, NOW);
        CourseOrder competing = order(UUID.randomUUID(), FIRST_USER, key, NOW.plusSeconds(1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> insertWhenReleased(first, ready, start));
            var competingResult = executor.submit(() -> insertWhenReleased(competing, ready, start));
            ready.await();
            start.countDown();

            CourseOrder storedFirst = firstResult.get();
            CourseOrder storedCompeting = competingResult.get();

            assertThat(storedCompeting).isEqualTo(storedFirst);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_orders", Integer.class)).isOne();
        }
    }

    private CourseOrder insertWhenReleased(
            CourseOrder order,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return orders.insertOrGet(order);
    }

    private CourseOrder order(UUID id, UUID userId, UUID key, Instant createdAt) {
        return new CourseOrder(
                id,
                userId,
                UUID.randomUUID(),
                "clean-architecture",
                "Clean Architecture",
                new Money(new BigDecimal("499000.00"), "VND"),
                key,
                createdAt,
                createdAt.plusSeconds(1800)
        );
    }
}
