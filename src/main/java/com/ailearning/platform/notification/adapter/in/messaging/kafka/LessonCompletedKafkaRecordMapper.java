package com.ailearning.platform.notification.adapter.in.messaging.kafka;

import com.ailearning.platform.learning.api.event.LessonCompletedEventV1;
import com.ailearning.platform.notification.api.contract.LessonCompletedNotificationCommand;
import com.ailearning.platform.notification.config.NotificationKafkaConsumerProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.StreamSupport;

@Component
public class LessonCompletedKafkaRecordMapper {
    static final String EVENT_ID_HEADER = "event_id";
    static final String EVENT_TYPE_HEADER = "event_type";
    static final String SCHEMA_VERSION_HEADER = "schema_version";
    static final String CONTENT_TYPE_HEADER = "content_type";

    private final ObjectMapper objectMapper;
    private final NotificationKafkaConsumerProperties properties;

    public LessonCompletedKafkaRecordMapper(
            ObjectMapper objectMapper,
            NotificationKafkaConsumerProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public LessonCompletedNotificationCommand toCommand(ConsumerRecord<String, String> record) {
        require(properties.topic().equals(record.topic()), "Unexpected Kafka topic");
        LessonCompletedEventV1 event = readEvent(record.value());
        requireComplete(event);
        require(event.userId().toString().equals(record.key()), "Kafka key does not match userId");
        require(event.eventId().toString().equals(header(record, EVENT_ID_HEADER)), "event_id header mismatch");
        require(event.eventType().equals(header(record, EVENT_TYPE_HEADER)), "event_type header mismatch");
        require(Integer.toString(event.schemaVersion()).equals(header(record, SCHEMA_VERSION_HEADER)),
                "schema_version header mismatch");
        require("application/json".equals(header(record, CONTENT_TYPE_HEADER)), "Unsupported content_type header");
        return new LessonCompletedNotificationCommand(event.eventId(), event.userId(), event.occurredAt());
    }

    private LessonCompletedEventV1 readEvent(String value) {
        require(value != null && !value.isBlank(), "Kafka payload is empty");
        try {
            return objectMapper.readValue(value, LessonCompletedEventV1.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Kafka payload is not a valid lesson completion event", exception);
        }
    }

    private void requireComplete(LessonCompletedEventV1 event) {
        require(event != null, "Kafka payload is empty");
        require(Objects.equals(LessonCompletedEventV1.EVENT_TYPE, event.eventType()), "Unsupported event type");
        require(event.schemaVersion() == LessonCompletedEventV1.SCHEMA_VERSION, "Unsupported schema version");
        require(event.eventId() != null, "eventId is required");
        require(event.occurredAt() != null, "occurredAt is required");
        require(event.progressId() != null, "progressId is required");
        require(event.userId() != null, "userId is required");
        require(event.enrollmentId() != null, "enrollmentId is required");
        require(event.courseId() != null, "courseId is required");
        require(event.lessonId() != null, "lessonId is required");
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        var headers = StreamSupport.stream(record.headers().headers(name).spliterator(), false).toList();
        require(headers.size() == 1, "Kafka header must occur exactly once: " + name);
        Header header = headers.getFirst();
        require(header.value() != null, "Kafka header is empty: " + name);
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
