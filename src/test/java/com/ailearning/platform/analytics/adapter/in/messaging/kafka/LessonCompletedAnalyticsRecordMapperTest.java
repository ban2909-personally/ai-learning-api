package com.ailearning.platform.analytics.adapter.in.messaging.kafka;

import com.ailearning.platform.analytics.config.AnalyticsKafkaConsumerProperties;
import com.ailearning.platform.learning.api.event.LessonCompletedEventV1;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LessonCompletedAnalyticsRecordMapperTest {
    private static final String TOPIC = "lesson-completed-analytics-test";
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final LessonCompletedAnalyticsRecordMapper mapper = new LessonCompletedAnalyticsRecordMapper(
            json,
            new AnalyticsKafkaConsumerProperties(
                    true, TOPIC, "analytics-test", Duration.ofSeconds(1), 3, TOPIC + ".dlt"
            )
    );

    @Test
    void mapsEveryRequiredDimensionFromACompleteVersionedRecord() throws Exception {
        LessonCompletedEventV1 event = event();

        var command = mapper.toCommand(record(event));

        assertThat(command.eventId()).isEqualTo(event.eventId());
        assertThat(command.userId()).isEqualTo(event.userId());
        assertThat(command.enrollmentId()).isEqualTo(event.enrollmentId());
        assertThat(command.courseId()).isEqualTo(event.courseId());
        assertThat(command.lessonId()).isEqualTo(event.lessonId());
        assertThat(command.occurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    void rejectsMismatchedIdentityAndAmbiguousHeaders() throws Exception {
        LessonCompletedEventV1 event = event();
        ConsumerRecord<String, String> duplicateHeader = record(event);
        addHeader(duplicateHeader, LessonCompletedAnalyticsRecordMapper.EVENT_ID_HEADER, event.eventId().toString());
        assertThatThrownBy(() -> mapper.toCommand(duplicateHeader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly once");

        ConsumerRecord<String, String> original = record(event);
        ConsumerRecord<String, String> wrongKey = new ConsumerRecord<>(
                original.topic(), original.partition(), original.offset(), UUID.randomUUID().toString(), original.value()
        );
        original.headers().forEach(wrongKey.headers()::add);
        assertThatThrownBy(() -> mapper.toCommand(wrongKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void rejectsIncompletePayload() throws Exception {
        LessonCompletedEventV1 incomplete = new LessonCompletedEventV1(
                UUID.randomUUID(),
                LessonCompletedEventV1.EVENT_TYPE,
                LessonCompletedEventV1.SCHEMA_VERSION,
                Instant.parse("2026-09-04T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null
        );

        assertThatThrownBy(() -> mapper.toCommand(record(incomplete)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lessonId");
    }

    private ConsumerRecord<String, String> record(LessonCompletedEventV1 event) throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC,
                0,
                1,
                event.userId().toString(),
                json.writeValueAsString(event)
        );
        addHeader(record, LessonCompletedAnalyticsRecordMapper.EVENT_ID_HEADER, event.eventId().toString());
        addHeader(record, LessonCompletedAnalyticsRecordMapper.EVENT_TYPE_HEADER, event.eventType());
        addHeader(record, LessonCompletedAnalyticsRecordMapper.SCHEMA_VERSION_HEADER,
                Integer.toString(event.schemaVersion()));
        addHeader(record, LessonCompletedAnalyticsRecordMapper.CONTENT_TYPE_HEADER, "application/json");
        return record;
    }

    private void addHeader(ConsumerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private LessonCompletedEventV1 event() {
        return new LessonCompletedEventV1(
                UUID.randomUUID(),
                LessonCompletedEventV1.EVENT_TYPE,
                LessonCompletedEventV1.SCHEMA_VERSION,
                Instant.parse("2026-09-04T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }
}
