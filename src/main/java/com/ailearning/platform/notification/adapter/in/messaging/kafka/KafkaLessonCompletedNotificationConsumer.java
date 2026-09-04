package com.ailearning.platform.notification.adapter.in.messaging.kafka;

import com.ailearning.platform.notification.api.usecase.NotificationUseCase;
import com.ailearning.platform.notification.config.NotificationKafkaConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notifications.consumer", name = "enabled", havingValue = "true")
public class KafkaLessonCompletedNotificationConsumer {
    private final LessonCompletedKafkaRecordMapper mapper;
    private final NotificationUseCase notifications;
    private final Counter projected;
    private final Counter duplicate;
    private final Counter rejected;

    public KafkaLessonCompletedNotificationConsumer(
            LessonCompletedKafkaRecordMapper mapper,
            NotificationUseCase notifications,
            MeterRegistry registry
    ) {
        this.mapper = mapper;
        this.notifications = notifications;
        this.projected = counter(registry, "projected");
        this.duplicate = counter(registry, "duplicate");
        this.rejected = counter(registry, "rejected");
    }

    @KafkaListener(
            topics = "${app.notifications.consumer.topic}",
            groupId = "${app.notifications.consumer.group-id}",
            containerFactory = NotificationKafkaConfig.CONTAINER_FACTORY
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            boolean created = notifications.projectLessonCompleted(mapper.toCommand(record)).isPresent();
            (created ? projected : duplicate).increment();
        } catch (RuntimeException exception) {
            rejected.increment();
            throw exception;
        }
    }

    private Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("notifications.kafka.processing")
                .description("Notification Kafka processing attempts")
                .tag("outcome", outcome)
                .register(registry);
    }
}
