package com.ailearning.platform.learning.adapter.out.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "app.messaging.learning-events", name = "enabled", havingValue = "true")
public class LearningEventBacklogMonitor {
    private static final String BACKLOG_SQL = """
            SELECT COUNT(*) AS pending_count,
                   COALESCE(EXTRACT(EPOCH FROM (:now - MIN(occurred_at))), 0) AS oldest_age_seconds
            FROM learning_event_outbox
            WHERE published_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    public LearningEventBacklogMonitor(DataSource dataSource, MeterRegistry registry, Clock clock) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.clock = clock;
        Gauge.builder("learning.events.outbox.pending", pending, AtomicLong::get)
                .description("Unpublished learning events in the transactional outbox")
                .register(registry);
        Gauge.builder("learning.events.outbox.oldest.age.seconds", oldestAgeSeconds, AtomicLong::get)
                .description("Age in seconds of the oldest unpublished learning event")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${app.messaging.learning-events.poll-delay}")
    void refresh() {
        Backlog backlog = jdbc.queryForObject(
                BACKLOG_SQL,
                new MapSqlParameterSource("now", Timestamp.from(clock.instant())),
                (resultSet, rowNumber) -> new Backlog(
                        resultSet.getLong("pending_count"),
                        resultSet.getLong("oldest_age_seconds")
                )
        );
        if (backlog != null) {
            pending.set(backlog.pending());
            oldestAgeSeconds.set(Math.max(backlog.oldestAgeSeconds(), 0));
        }
    }

    private record Backlog(long pending, long oldestAgeSeconds) {
    }
}
