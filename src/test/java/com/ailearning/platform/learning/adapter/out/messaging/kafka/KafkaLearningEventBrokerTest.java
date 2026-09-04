package com.ailearning.platform.learning.adapter.out.messaging.kafka;

import com.ailearning.platform.learning.application.port.out.LearningEventMessage;
import com.ailearning.platform.learning.config.LearningEventsProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaLearningEventBrokerTest {
    private final KafkaTemplate<String, String> kafka = mockKafkaTemplate();

    @Test
    void failsWhenKafkaDoesNotAcknowledgeBeforeTheBoundedTimeout() {
        when(kafka.send(ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(new CompletableFuture<>());
        var broker = new KafkaLearningEventBroker(kafka, properties(Duration.ofMillis(1)));

        assertThatThrownBy(() -> broker.publish(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka did not acknowledge the learning event");
    }

    @Test
    void preservesTheInterruptSignal() {
        when(kafka.send(ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(new CompletableFuture<>());
        var broker = new KafkaLearningEventBroker(kafka, properties(Duration.ofSeconds(1)));

        try {
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> broker.publish(message()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Kafka publication was interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private LearningEventMessage message() {
        return new LearningEventMessage(
                UUID.randomUUID(),
                "lesson.completed",
                1,
                UUID.randomUUID().toString(),
                "{}",
                Instant.parse("2026-09-04T00:00:00Z"),
                0
        );
    }

    private LearningEventsProperties properties(Duration sendTimeout) {
        return new LearningEventsProperties(
                true,
                "lesson-completed",
                25,
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                sendTimeout,
                1,
                Duration.ofSeconds(2),
                Duration.ofMinutes(5)
        );
    }

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<String, String> mockKafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}
