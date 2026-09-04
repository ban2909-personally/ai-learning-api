package com.ailearning.platform.learning.adapter.out.persistence;

import com.ailearning.platform.learning.api.event.LessonCompletedEventV1;
import com.ailearning.platform.learning.domain.event.LessonCompleted;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LearningEventOutboxPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_outbox_test")
            .withUsername("test")
            .withPassword("test");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static PGSimpleDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static LearningEventOutboxPersistenceAdapter outbox;

    @BeforeAll
    static void migrateDatabase() {
        dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        outbox = new LearningEventOutboxPersistenceAdapter(dataSource, OBJECT_MAPPER);
    }

    @BeforeEach
    void clearOutbox() {
        jdbc.update("DELETE FROM learning_event_outbox");
    }

    @Test
    void storesOneVersionedMinimalPayloadForAnAggregateCompletion() throws Exception {
        LessonCompleted first = event(UUID.randomUUID(), UUID.randomUUID());
        LessonCompleted duplicate = event(UUID.randomUUID(), first.progressId());

        outbox.append(first);
        outbox.append(duplicate);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_event_outbox", Integer.class))
                .isOne();
        String payload = jdbc.queryForObject(
                "SELECT payload::TEXT FROM learning_event_outbox WHERE event_id = ?",
                String.class,
                first.eventId()
        );
        JsonNode json = OBJECT_MAPPER.readTree(payload);
        assertThat(json.size()).isEqualTo(9);
        assertThat(json.path("eventId").asText()).isEqualTo(first.eventId().toString());
        assertThat(json.path("eventType").asText()).isEqualTo(LessonCompletedEventV1.EVENT_TYPE);
        assertThat(json.path("schemaVersion").asInt()).isEqualTo(LessonCompletedEventV1.SCHEMA_VERSION);
        assertThat(json.path("progressId").asText()).isEqualTo(first.progressId().toString());
        assertThat(json.has("email")).isFalse();
        assertThat(json.has("name")).isFalse();
    }

    @Test
    void leasePreventsAnotherWorkerUntilItExpires() {
        LessonCompleted event = event(UUID.randomUUID(), UUID.randomUUID());
        outbox.append(event);

        var firstClaim = outbox.claimAvailable("worker-1", 10, NOW, NOW.plusSeconds(30));
        var competingClaim = outbox.claimAvailable("worker-2", 10, NOW.plusSeconds(1), NOW.plusSeconds(31));
        var recoveredClaim = outbox.claimAvailable("worker-2", 10, NOW.plusSeconds(31), NOW.plusSeconds(61));

        assertThat(firstClaim).singleElement().satisfies(message -> {
            assertThat(message.eventId()).isEqualTo(event.eventId());
            assertThat(message.messageKey()).isEqualTo(event.userId().toString());
            assertThat(message.attempts()).isZero();
        });
        assertThat(competingClaim).isEmpty();
        assertThat(recoveredClaim).extracting(message -> message.eventId())
                .containsExactly(event.eventId());
    }

    @Test
    void concurrentWorkersCannotClaimTheSameAvailableEvent() throws Exception {
        LessonCompleted event = event(UUID.randomUUID(), UUID.randomUUID());
        outbox.append(event);
        Callable<Integer> first = () -> outbox.claimAvailable(
                "worker-1",
                10,
                NOW,
                NOW.plusSeconds(30)
        ).size();
        Callable<Integer> second = () -> outbox.claimAvailable(
                "worker-2",
                10,
                NOW,
                NOW.plusSeconds(30)
        ).size();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(java.util.List.of(first, second));
            int claimed = results.get(0).get() + results.get(1).get();
            assertThat(claimed).isOne();
        }
    }

    @Test
    void reschedulesFailuresAndStopsClaimingPublishedEvents() {
        LessonCompleted event = event(UUID.randomUUID(), UUID.randomUUID());
        outbox.append(event);
        outbox.claimAvailable("worker-1", 10, NOW, NOW.plusSeconds(30));

        outbox.reschedule(event.eventId(), "worker-1", NOW.plusSeconds(5), "broker_publish_failed");

        assertThat(outbox.claimAvailable("worker-2", 10, NOW.plusSeconds(4), NOW.plusSeconds(34)))
                .isEmpty();
        var retry = outbox.claimAvailable("worker-2", 10, NOW.plusSeconds(5), NOW.plusSeconds(35));
        assertThat(retry).singleElement().satisfies(message -> assertThat(message.attempts()).isOne());

        outbox.markPublished(event.eventId(), "worker-2", NOW.plusSeconds(6));

        assertThat(outbox.claimAvailable("worker-3", 10, NOW.plusSeconds(40), NOW.plusSeconds(70)))
                .isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT published_at IS NOT NULL FROM learning_event_outbox WHERE event_id = ?",
                Boolean.class,
                event.eventId()
        )).isTrue();
    }

    private LessonCompleted event(UUID eventId, UUID progressId) {
        return new LessonCompleted(
                eventId,
                progressId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW
        );
    }
}
