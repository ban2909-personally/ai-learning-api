package com.ailearning.platform.learning.adapter.out.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LearningEventBacklogMonitorIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_backlog_metrics_test")
            .withUsername("test")
            .withPassword("test");

    private static PGSimpleDataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM learning_event_outbox");
    }

    @Test
    void gaugesOnlyUnpublishedRowsAndReportsTheOldestAge() {
        insertOutbox(NOW.minusSeconds(120), null);
        insertOutbox(NOW.minusSeconds(30), null);
        insertOutbox(NOW.minusSeconds(300), NOW.minusSeconds(290));
        var registry = new SimpleMeterRegistry();
        var monitor = new LearningEventBacklogMonitor(
                dataSource,
                registry,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        monitor.refresh();

        assertThat(registry.get("learning.events.outbox.pending").gauge().value()).isEqualTo(2);
        assertThat(registry.get("learning.events.outbox.oldest.age.seconds").gauge().value()).isEqualTo(120);
    }

    private void insertOutbox(Instant occurredAt, Instant publishedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO learning_event_outbox (
                    event_id, event_type, schema_version, aggregate_type, aggregate_id,
                    message_key, payload, occurred_at, available_at, published_at
                ) VALUES (?, 'lesson.completed', 1, 'lesson_progress', ?, ?, '{}'::jsonb, ?, ?, ?)
                """,
                id,
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt),
                publishedAt == null ? null : Timestamp.from(publishedAt)
        );
    }
}
