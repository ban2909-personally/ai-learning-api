package com.ailearning.platform.notification.adapter.in.messaging.kafka;

import com.ailearning.platform.notification.api.contract.LessonCompletedNotificationCommand;
import com.ailearning.platform.notification.api.contract.NotificationView;
import com.ailearning.platform.notification.api.usecase.NotificationUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaLessonCompletedNotificationConsumerTest {
    private final LessonCompletedKafkaRecordMapper mapper = mock(LessonCompletedKafkaRecordMapper.class);
    private final NotificationUseCase notifications = mock(NotificationUseCase.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final KafkaLessonCompletedNotificationConsumer consumer =
            new KafkaLessonCompletedNotificationConsumer(mapper, notifications, registry);

    @Test
    void recordsProjectedAndDuplicateOutcomes() {
        ConsumerRecord<String, String> projectedRecord = record(1);
        ConsumerRecord<String, String> duplicateRecord = record(2);
        LessonCompletedNotificationCommand projectedCommand = command();
        LessonCompletedNotificationCommand duplicateCommand = command();
        when(mapper.toCommand(projectedRecord)).thenReturn(projectedCommand);
        when(mapper.toCommand(duplicateRecord)).thenReturn(duplicateCommand);
        when(notifications.projectLessonCompleted(projectedCommand)).thenReturn(Optional.of(mock(NotificationView.class)));
        when(notifications.projectLessonCompleted(duplicateCommand)).thenReturn(Optional.empty());

        consumer.consume(projectedRecord);
        consumer.consume(duplicateRecord);

        assertThat(counter("projected")).isEqualTo(1);
        assertThat(counter("duplicate")).isEqualTo(1);
        assertThat(counter("rejected")).isZero();
    }

    @Test
    void propagatesRejectedRecordsForContainerRetry() {
        ConsumerRecord<String, String> record = record(3);
        when(mapper.toCommand(record)).thenThrow(new IllegalArgumentException("invalid envelope"));

        assertThatThrownBy(() -> consumer.consume(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid envelope");
        assertThat(counter("rejected")).isEqualTo(1);
    }

    private ConsumerRecord<String, String> record(long offset) {
        return new ConsumerRecord<>("topic", 0, offset, "key", "value");
    }

    private LessonCompletedNotificationCommand command() {
        return new LessonCompletedNotificationCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-09-04T10:00:00Z")
        );
    }

    private double counter(String outcome) {
        return registry.counter("notifications.kafka.processing", "outcome", outcome).count();
    }
}
