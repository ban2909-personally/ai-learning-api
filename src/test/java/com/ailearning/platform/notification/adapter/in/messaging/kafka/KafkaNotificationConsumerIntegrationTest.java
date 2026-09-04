package com.ailearning.platform.notification.adapter.in.messaging.kafka;

import com.ailearning.platform.learning.api.event.LessonCompletedEventV1;
import com.ailearning.platform.notification.application.port.out.NotificationRealtimeDelivery;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.notifications.consumer.enabled=true",
        "app.notifications.consumer.topic=" + KafkaNotificationConsumerIntegrationTest.TOPIC,
        "app.notifications.consumer.group-id=notification-consumer-integration",
        "app.notifications.consumer.retry-delay=PT1S",
        "app.messaging.learning-events.enabled=false"
})
@Import(KafkaNotificationConsumerIntegrationTest.TopicConfiguration.class)
class KafkaNotificationConsumerIntegrationTest {
    static final String TOPIC = "notification.lesson-completed.integration";

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
    }
}
