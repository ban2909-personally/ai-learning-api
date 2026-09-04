package com.ailearning.platform.notification.adapter.in.messaging.kafka;

import com.ailearning.platform.learning.api.event.LessonCompletedEventV1;
import com.ailearning.platform.notification.config.NotificationKafkaConsumerProperties;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LessonCompletedKafkaRecordMapperTest {
    private static final String TOPIC = "lesson-completed-test";
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final LessonCompletedKafkaRecordMapper mapper = new LessonCompletedKafkaRecordMapper(
            json,
            new NotificationKafkaConsumerProperties(
                    true, TOPIC, "notifications-test", Duration.ofSeconds(1), 3, TOPIC + ".dlt"
            )
    );

    @Test
    void mapsACompleteVersionedRecord() throws Exception {
        LessonCompletedEventV1 event = event();
        ConsumerRecord<String, String> record = record(event);

        var command = mapper.toCommand(record);

        assertThat(command.eventId()).isEqualTo(event.eventId());
        assertThat(command.userId()).isEqualTo(event.userId());
        assertThat(command.occurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    void rejectsAnAmbiguousOrMismatchedEnvelope() throws Exception {
        LessonCompletedEventV1 event = event();
        ConsumerRecord<String, String> duplicateHeader = record(event);
        addHeader(duplicateHeader, LessonCompletedKafkaRecordMapper.EVENT_ID_HEADER, event.eventId().toString());
        assertThatThrownBy(() -> mapper.toCommand(duplicateHeader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly once");

        ConsumerRecord<String, String> wrongKey = record(event);
        ConsumerRecord<String, String> changedKey = new ConsumerRecord<>(
                wrongKey.topic(),
                wrongKey.partition(),
                wrongKey.offset(),
                UUID.randomUUID().toString(),
                wrongKey.value()
        );
        wrongKey.headers().forEach(header -> changedKey.headers().add(header));
        assertThatThrownBy(() -> mapper.toCommand(changedKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void rejectsIncompletePayloadInsteadOfPartiallyProjectingIt() throws Exception {
        LessonCompletedEventV1 incomplete = new LessonCompletedEventV1(
                UUID.randomUUID(),
                LessonCompletedEventV1.EVENT_TYPE,
                LessonCompletedEventV1.SCHEMA_VERSION,
                Instant.parse("2026-09-04T10:00:00Z"),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> mapper.toCommand(record(incomplete)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("progressId");
    }

    private ConsumerRecord<String, String> record(LessonCompletedEventV1 event) throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC,
                0,
                1,
                event.userId().toString(),
                json.writeValueAsString(event)
        );
        addHeader(record, LessonCompletedKafkaRecordMapper.EVENT_ID_HEADER, event.eventId().toString());
        addHeader(record, LessonCompletedKafkaRecordMapper.EVENT_TYPE_HEADER, event.eventType());
        addHeader(record, LessonCompletedKafkaRecordMapper.SCHEMA_VERSION_HEADER,
                Integer.toString(event.schemaVersion()));
        addHeader(record, LessonCompletedKafkaRecordMapper.CONTENT_TYPE_HEADER, "application/json");
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
