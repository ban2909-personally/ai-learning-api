package com.ailearning.platform.learning.adapter.out.messaging.kafka;

import com.ailearning.platform.learning.application.port.out.LearningEventMessage;
import com.ailearning.platform.learning.config.LearningEventsProperties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class KafkaLearningEventBrokerIntegrationTest {
    private static final String TOPIC = "ai-learning.learning.lesson-completed.v1.test";

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

    @BeforeAll
    static void createTopic() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        ))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void publishesVersionedJsonWithStableKeyAndHeaders() {
        Map<String, Object> producerConfiguration = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true
        );
        var producerFactory = new DefaultKafkaProducerFactory<String, String>(producerConfiguration);
        var kafkaTemplate = new KafkaTemplate<>(producerFactory);
        var broker = new KafkaLearningEventBroker(kafkaTemplate, properties());
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"lesson.completed","schemaVersion":1,"userId":"%s"}
                """.formatted(eventId, userId).strip();
        var message = new LearningEventMessage(
                eventId,
                "lesson.completed",
                1,
                userId.toString(),
                payload,
                Instant.parse("2026-09-03T00:00:00Z"),
                0
        );

        try {
            broker.publish(message);

            try (KafkaConsumer<String, String> consumer = consumer()) {
                consumer.subscribe(List.of(TOPIC));
                var records = consumer.poll(Duration.ofSeconds(10));

                assertThat(records).hasSize(1);
                var record = records.iterator().next();
                assertThat(record.key()).isEqualTo(userId.toString());
                assertThat(record.value()).isEqualTo(payload);
                assertThat(header(record.headers().lastHeader(KafkaLearningEventBroker.EVENT_ID_HEADER)))
                        .isEqualTo(eventId.toString());
                assertThat(header(record.headers().lastHeader(KafkaLearningEventBroker.EVENT_TYPE_HEADER)))
                        .isEqualTo("lesson.completed");
                assertThat(header(record.headers().lastHeader(KafkaLearningEventBroker.SCHEMA_VERSION_HEADER)))
                        .isEqualTo("1");
                assertThat(header(record.headers().lastHeader(KafkaLearningEventBroker.CONTENT_TYPE_HEADER)))
                        .isEqualTo("application/json");
            }
        } finally {
            kafkaTemplate.destroy();
            producerFactory.destroy();
        }
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "learning-event-contract-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }

    private LearningEventsProperties properties() {
        return new LearningEventsProperties(
                true,
                TOPIC,
                10,
                Duration.ofSeconds(1),
                Duration.ofMinutes(5),
                Duration.ofSeconds(10),
                10_000,
                Duration.ofSeconds(2),
                Duration.ofMinutes(5)
        );
    }

    private String header(Header header) {
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
