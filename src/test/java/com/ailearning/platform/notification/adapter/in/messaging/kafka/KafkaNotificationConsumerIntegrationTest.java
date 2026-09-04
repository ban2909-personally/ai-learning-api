package com.ailearning.platform.notification.adapter.in.messaging.kafka;

import com.ailearning.platform.learning.api.event.LessonCompletedEventV1;
import com.ailearning.platform.notification.application.port.out.NotificationRealtimeDelivery;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "app.notifications.consumer.enabled=true",
        "app.notifications.consumer.topic=" + KafkaNotificationConsumerIntegrationTest.TOPIC,
        "app.notifications.consumer.group-id=notification-consumer-integration",
        "app.notifications.consumer.retry-delay=PT0.001S",
        "app.notifications.consumer.max-attempts=2",
        "app.notifications.consumer.dead-letter-topic=" + KafkaNotificationConsumerIntegrationTest.DLT_TOPIC,
        "app.messaging.learning-events.enabled=false"
})
@Import(KafkaNotificationConsumerIntegrationTest.TopicConfiguration.class)
class KafkaNotificationConsumerIntegrationTest {
    static final String TOPIC = "notification.lesson-completed.integration";
    static final String DLT_TOPIC = TOPIC + ".dlt";

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_notification_kafka_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @MockitoBean
    private NotificationRealtimeDelivery realtime;

    @Test
    void projectsDuplicateKafkaDeliveriesExactlyOnce() throws Exception {
        LessonCompletedEventV1 event = event();

        kafka.send(record(event)).get(10, TimeUnit.SECONDS);
        kafka.send(record(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Long stored = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_notifications WHERE id = ?",
                    Long.class,
                    event.eventId()
            );
            assertThat(stored).isEqualTo(1);
            assertThat(counter("projected")).isEqualTo(1);
            assertThat(counter("duplicate")).isEqualTo(1);
        });
        verify(realtime, times(1)).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishesAPoisonRecordToTheConfiguredDeadLetterTopicAfterFiniteRetries() throws Exception {
        try (Consumer<String, String> deadLetters = consumerFactory.createConsumer(
                "notification-dlt-test-" + UUID.randomUUID(),
                "notification-dlt-client"
        )) {
            deadLetters.subscribe(List.of(DLT_TOPIC));
            kafka.send(TOPIC, UUID.randomUUID().toString(), "not-json").get(10, TimeUnit.SECONDS);

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                var records = deadLetters.poll(Duration.ofMillis(250));
                assertThat(records).isNotEmpty();
                assertThat(records.iterator().next().value()).isEqualTo("not-json");
                assertThat(registry.counter("notifications.kafka.dead.letter").count()).isEqualTo(1);
            });
        }
    }

    private ProducerRecord<String, String> record(LessonCompletedEventV1 event) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                TOPIC,
                event.userId().toString(),
                objectMapper.writeValueAsString(event)
        );
        addHeader(record, LessonCompletedKafkaRecordMapper.EVENT_ID_HEADER, event.eventId().toString());
        addHeader(record, LessonCompletedKafkaRecordMapper.EVENT_TYPE_HEADER, event.eventType());
        addHeader(record, LessonCompletedKafkaRecordMapper.SCHEMA_VERSION_HEADER,
                Integer.toString(event.schemaVersion()));
        addHeader(record, LessonCompletedKafkaRecordMapper.CONTENT_TYPE_HEADER, "application/json");
        return record;
    }

    private void addHeader(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private double counter(String outcome) {
        return registry.counter("notifications.kafka.processing", "outcome", outcome).count();
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TopicConfiguration {
        @Bean
        NewTopic notificationIntegrationTopic() {
            return new NewTopic(TOPIC, 1, (short) 1);
        }

        @Bean
        NewTopic notificationDeadLetterIntegrationTopic() {
            return new NewTopic(DLT_TOPIC, 1, (short) 1);
        }
    }
}
