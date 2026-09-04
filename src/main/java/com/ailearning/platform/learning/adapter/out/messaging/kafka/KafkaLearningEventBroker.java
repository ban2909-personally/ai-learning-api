package com.ailearning.platform.learning.adapter.out.messaging.kafka;

import com.ailearning.platform.learning.application.port.out.LearningEventBroker;
import com.ailearning.platform.learning.application.port.out.LearningEventMessage;
import com.ailearning.platform.learning.config.LearningEventsProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(prefix = "app.messaging.learning-events", name = "enabled", havingValue = "true")
public class KafkaLearningEventBroker implements LearningEventBroker {
    static final String EVENT_ID_HEADER = "event_id";
    static final String EVENT_TYPE_HEADER = "event_type";
    static final String SCHEMA_VERSION_HEADER = "schema_version";
    static final String CONTENT_TYPE_HEADER = "content_type";

    private final KafkaTemplate<String, String> kafka;
    private final LearningEventsProperties properties;

    public KafkaLearningEventBroker(
            KafkaTemplate<String, String> kafka,
            LearningEventsProperties properties
    ) {
        this.kafka = kafka;
        this.properties = properties;
    }

    @Override
    public void publish(LearningEventMessage message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                properties.topic(),
                message.messageKey(),
                message.payload()
        );
        addHeader(record, EVENT_ID_HEADER, message.eventId().toString());
        addHeader(record, EVENT_TYPE_HEADER, message.eventType());
        addHeader(record, SCHEMA_VERSION_HEADER, Integer.toString(message.schemaVersion()));
        addHeader(record, CONTENT_TYPE_HEADER, "application/json");

        try {
            kafka.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publication was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Kafka did not acknowledge the learning event", exception);
        }
    }

    private void addHeader(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
