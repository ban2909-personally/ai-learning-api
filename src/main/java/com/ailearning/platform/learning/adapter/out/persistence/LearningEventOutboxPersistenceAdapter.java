package com.ailearning.platform.learning.adapter.out.persistence;

import com.ailearning.platform.learning.api.event.LessonCompletedEventV1;
import com.ailearning.platform.learning.application.port.out.LearningEventMessage;
import com.ailearning.platform.learning.application.port.out.LearningEventOutbox;
import com.ailearning.platform.learning.domain.event.LessonCompleted;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class LearningEventOutboxPersistenceAdapter implements LearningEventOutbox {
    private static final String AGGREGATE_TYPE = "lesson_progress";

    private static final String APPEND_SQL = """
            INSERT INTO learning_event_outbox (
                event_id,
                event_type,
                schema_version,
                aggregate_type,
                aggregate_id,
                message_key,
                payload,
                occurred_at,
                available_at
            ) VALUES (
                :eventId,
                :eventType,
                :schemaVersion,
                :aggregateType,
                :aggregateId,
                :messageKey,
                CAST(:payload AS JSONB),
                :occurredAt,
                :occurredAt
            )
            ON CONFLICT (aggregate_id, event_type) DO NOTHING
            """;

    private static final String CLAIM_SQL = """
            WITH candidates AS (
                SELECT event_id
                FROM learning_event_outbox
                WHERE published_at IS NULL
                  AND available_at <= :now
                  AND (locked_until IS NULL OR locked_until < :now)
                ORDER BY available_at, occurred_at, event_id
                FOR UPDATE SKIP LOCKED
                LIMIT :limit
            )
            UPDATE learning_event_outbox AS event
            SET locked_by = :owner,
                locked_until = :lockedUntil
            FROM candidates
            WHERE event.event_id = candidates.event_id
            RETURNING
                event.event_id,
                event.event_type,
                event.schema_version,
                event.message_key,
                event.payload::TEXT AS payload,
                event.occurred_at,
                event.attempts
            """;

    private static final String MARK_PUBLISHED_SQL = """
            UPDATE learning_event_outbox
            SET published_at = :publishedAt,
                locked_by = NULL,
                locked_until = NULL,
                last_failure_code = NULL
            WHERE event_id = :eventId
              AND locked_by = :owner
              AND published_at IS NULL
            """;

    private static final String RESCHEDULE_SQL = """
            UPDATE learning_event_outbox
            SET attempts = attempts + 1,
                available_at = :availableAt,
                locked_by = NULL,
                locked_until = NULL,
                last_failure_code = :failureCode
            WHERE event_id = :eventId
              AND locked_by = :owner
              AND published_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public LearningEventOutboxPersistenceAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(LessonCompleted event) {
        LessonCompletedEventV1 payload = new LessonCompletedEventV1(
                event.eventId(),
                LessonCompletedEventV1.EVENT_TYPE,
                LessonCompletedEventV1.SCHEMA_VERSION,
                event.occurredAt(),
                event.progressId(),
                event.userId(),
                event.enrollmentId(),
                event.courseId(),
                event.lessonId()
        );

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventId", event.eventId())
                .addValue("eventType", LessonCompletedEventV1.EVENT_TYPE)
                .addValue("schemaVersion", LessonCompletedEventV1.SCHEMA_VERSION)
                .addValue("aggregateType", AGGREGATE_TYPE)
                .addValue("aggregateId", event.progressId())
                .addValue("messageKey", event.userId().toString())
                .addValue("payload", serialize(payload))
                .addValue("occurredAt", Timestamp.from(event.occurredAt()));
        jdbc.update(APPEND_SQL, parameters);
    }

    @Override
    public List<LearningEventMessage> claimAvailable(
            String owner,
            int limit,
            Instant now,
            Instant lockedUntil
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("owner", owner)
                .addValue("limit", limit)
                .addValue("now", Timestamp.from(now))
                .addValue("lockedUntil", Timestamp.from(lockedUntil));
        return jdbc.query(CLAIM_SQL, parameters, this::mapMessage);
    }

    @Override
    public void markPublished(UUID eventId, String owner, Instant publishedAt) {
        MapSqlParameterSource parameters = ownershipParameters(eventId, owner)
                .addValue("publishedAt", Timestamp.from(publishedAt));
        requireUpdated(jdbc.update(MARK_PUBLISHED_SQL, parameters), eventId);
    }

    @Override
    public void reschedule(UUID eventId, String owner, Instant availableAt, String failureCode) {
        MapSqlParameterSource parameters = ownershipParameters(eventId, owner)
                .addValue("availableAt", Timestamp.from(availableAt))
                .addValue("failureCode", failureCode);
        requireUpdated(jdbc.update(RESCHEDULE_SQL, parameters), eventId);
    }

    private String serialize(LessonCompletedEventV1 event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the learning integration event", exception);
        }
    }

    private LearningEventMessage mapMessage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LearningEventMessage(
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getInt("schema_version"),
                resultSet.getString("message_key"),
                resultSet.getString("payload"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getInt("attempts")
        );
    }

    private MapSqlParameterSource ownershipParameters(UUID eventId, String owner) {
        return new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("owner", owner);
    }

    private void requireUpdated(int updatedRows, UUID eventId) {
        if (updatedRows != 1) {
            throw new IllegalStateException("Learning event lease is no longer owned: " + eventId);
        }
    }
}
