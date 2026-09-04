package com.ailearning.platform.analytics.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@EnableKafka
@Configuration
@ConditionalOnProperty(prefix = "app.analytics.consumer", name = "enabled", havingValue = "true")
public class AnalyticsKafkaConfig {
    public static final String CONTAINER_FACTORY = "analyticsKafkaListenerContainerFactory";

    @Bean(name = CONTAINER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<String, String> analyticsKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            AnalyticsKafkaConsumerProperties properties,
            MeterRegistry registry
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), record.partition())
        );
        recoverer.setFailIfSendResultIsError(true);
        Counter deadLettered = Counter.builder("analytics.kafka.dead.letter")
                .description("Analytics records published to the dead-letter topic")
                .register(registry);
        var errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    recoverer.accept(record, exception);
                    deadLettered.increment();
                },
                new FixedBackOff(properties.retryDelay().toMillis(), properties.maxAttempts() - 1L)
        );
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
