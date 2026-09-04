package com.ailearning.platform.analytics.adapter.in.messaging.kafka;

import com.ailearning.platform.analytics.api.contract.ProjectLessonCompletionCommand;
import com.ailearning.platform.analytics.api.usecase.LearningAnalyticsUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaLessonCompletedAnalyticsConsumerTest {
    private final LessonCompletedAnalyticsRecordMapper mapper = mock(LessonCompletedAnalyticsRecordMapper.class);
    private final LearningAnalyticsUseCase analytics = mock(LearningAnalyticsUseCase.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final KafkaLessonCompletedAnalyticsConsumer consumer =
            new KafkaLessonCompletedAnalyticsConsumer(mapper, analytics, registry);

    @Test
    void recordsProjectedAndDuplicateOutcomes() {
        ConsumerRecord<String, String> projectedRecord = record(1);
        ConsumerRecord<String, String> duplicateRecord = record(2);
        ProjectLessonCompletionCommand projectedCommand = command();
        ProjectLessonCompletionCommand duplicateCommand = command();
        when(mapper.toCommand(projectedRecord)).thenReturn(projectedCommand);
        when(mapper.toCommand(duplicateRecord)).thenReturn(duplicateCommand);
        when(analytics.projectLessonCompleted(projectedCommand)).thenReturn(true);
        when(analytics.projectLessonCompleted(duplicateCommand)).thenReturn(false);

        consumer.consume(projectedRecord);
        consumer.consume(duplicateRecord);

        assertThat(counter("projected")).isEqualTo(1);
        assertThat(counter("duplicate")).isEqualTo(1);
        assertThat(counter("rejected")).isZero();
    }

    @Test
    void propagatesRejectedRecordsForContainerRecovery() {
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

    private ProjectLessonCompletionCommand command() {
        return new ProjectLessonCompletionCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-09-04T10:00:00Z")
        );
    }

    private double counter(String outcome) {
        return registry.counter("analytics.kafka.processing", "outcome", outcome).count();
    }
}
