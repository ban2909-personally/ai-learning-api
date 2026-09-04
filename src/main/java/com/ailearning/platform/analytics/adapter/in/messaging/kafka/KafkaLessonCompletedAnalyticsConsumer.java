package com.ailearning.platform.analytics.adapter.in.messaging.kafka;

import com.ailearning.platform.analytics.api.usecase.LearningAnalyticsUseCase;
import com.ailearning.platform.analytics.config.AnalyticsKafkaConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.analytics.consumer", name = "enabled", havingValue = "true")
public class KafkaLessonCompletedAnalyticsConsumer {
    private final LessonCompletedAnalyticsRecordMapper mapper;
    private final LearningAnalyticsUseCase analytics;
    private final Counter projected;
    private final Counter duplicate;
    private final Counter rejected;

    public KafkaLessonCompletedAnalyticsConsumer(
            LessonCompletedAnalyticsRecordMapper mapper,
            LearningAnalyticsUseCase analytics,
            MeterRegistry registry
    ) {
        this.mapper = mapper;
        this.analytics = analytics;
        this.projected = counter(registry, "projected");
        this.duplicate = counter(registry, "duplicate");
        this.rejected = counter(registry, "rejected");
    }

    @Transactional
    @KafkaListener(
            topics = "${app.analytics.consumer.topic}",
            groupId = "${app.analytics.consumer.group-id}",
            containerFactory = AnalyticsKafkaConfig.CONTAINER_FACTORY
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            boolean created = analytics.projectLessonCompleted(mapper.toCommand(record));
            (created ? projected : duplicate).increment();
        } catch (RuntimeException exception) {
            rejected.increment();
            throw exception;
        }
    }

    private Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("analytics.kafka.processing")
                .description("Learning analytics Kafka processing attempts")
                .tag("outcome", outcome)
                .register(registry);
    }
}
