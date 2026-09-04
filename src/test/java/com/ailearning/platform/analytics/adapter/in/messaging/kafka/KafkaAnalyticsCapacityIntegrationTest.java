package com.ailearning.platform.analytics.adapter.in.messaging.kafka;

import com.ailearning.platform.learning.api.event.LessonCompletedEventV1;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.analytics.consumer.enabled=true",
        "app.analytics.consumer.topic=" + KafkaAnalyticsCapacityIntegrationTest.TOPIC,
        "app.analytics.consumer.group-id=analytics-capacity-integration",
        "app.analytics.consumer.retry-delay=PT0.001S",
        "app.analytics.consumer.max-attempts=2",
        "app.analytics.consumer.dead-letter-topic=" + KafkaAnalyticsCapacityIntegrationTest.DLT_TOPIC,
        "spring.kafka.consumer.max-poll-records=10",
        "app.messaging.learning-events.enabled=false",
        "app.notifications.consumer.enabled=false"
})
@Import(KafkaAnalyticsCapacityIntegrationTest.TopicConfiguration.class)
class KafkaAnalyticsCapacityIntegrationTest {
    static final String TOPIC = "analytics.lesson-completed.capacity";
    static final String DLT_TOPIC = TOPIC + ".dlt";
    private static final int UNIQUE_EVENTS = 60;
    private static final int DUPLICATE_EVENTS = 20;
    private static final Instant FIRST_COMPLETION = Instant.parse("2026-09-04T12:00:00Z");

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_analytics_capacity_test")
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

    @Test
    void preservesIdempotencyAndLatestCompletionAcrossBoundedPolls() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        List<LessonCompletedEventV1> events = new ArrayList<>();
        for (int index = 0; index < UNIQUE_EVENTS; index++) {
            events.add(event(userId, courseId, index));
        }

        List<CompletableFuture<?>> sends = new ArrayList<>();
        for (LessonCompletedEventV1 event : events) {
            sends.add(kafka.send(record(event)));
        }
        for (int index = 0; index < DUPLICATE_EVENTS; index++) {
            sends.add(kafka.send(record(events.get(index * 3))));
        }
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Long stored = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM learning_completion_facts WHERE user_id = ?",
                    Long.class,
                    userId
            );
            Timestamp lastCompletion = jdbc.queryForObject(
                    "SELECT MAX(completed_at) FROM learning_completion_facts WHERE user_id = ?",
                    Timestamp.class,
                    userId
            );
            assertThat(stored).isEqualTo(UNIQUE_EVENTS);
            assertThat(lastCompletion).isNotNull();
            assertThat(lastCompletion.toInstant()).isEqualTo(FIRST_COMPLETION.plusSeconds(UNIQUE_EVENTS - 1L));
            assertThat(counter("projected")).isEqualTo(UNIQUE_EVENTS);
            assertThat(counter("duplicate")).isEqualTo(DUPLICATE_EVENTS);
            assertThat(counter("rejected")).isZero();
            assertThat(registry.counter("analytics.kafka.dead.letter").count()).isZero();
        });
    }

    private ProducerRecord<String, String> record(LessonCompletedEventV1 event) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                TOPIC,
                event.userId().toString(),
                objectMapper.writeValueAsString(event)
        );
        addHeader(record, LessonCompletedAnalyticsRecordMapper.EVENT_ID_HEADER, event.eventId().toString());
        addHeader(record, LessonCompletedAnalyticsRecordMapper.EVENT_TYPE_HEADER, event.eventType());
        addHeader(record, LessonCompletedAnalyticsRecordMapper.SCHEMA_VERSION_HEADER,
                Integer.toString(event.schemaVersion()));
        addHeader(record, LessonCompletedAnalyticsRecordMapper.CONTENT_TYPE_HEADER, "application/json");
        return record;
    }

    private void addHeader(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private LessonCompletedEventV1 event(UUID userId, UUID courseId, int index) {
        return new LessonCompletedEventV1(
                UUID.randomUUID(),
                LessonCompletedEventV1.EVENT_TYPE,
                LessonCompletedEventV1.SCHEMA_VERSION,
                FIRST_COMPLETION.plusSeconds(index),
                UUID.randomUUID(),
                userId,
                UUID.randomUUID(),
                courseId,
                UUID.randomUUID()
        );
    }

    private double counter(String outcome) {
        return registry.counter("analytics.kafka.processing", "outcome", outcome).count();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TopicConfiguration {
        @Bean
        NewTopic analyticsCapacityTopic() {
            return new NewTopic(TOPIC, 1, (short) 1);
        }

        @Bean
        NewTopic analyticsCapacityDeadLetterTopic() {
            return new NewTopic(DLT_TOPIC, 1, (short) 1);
        }
    }
}
