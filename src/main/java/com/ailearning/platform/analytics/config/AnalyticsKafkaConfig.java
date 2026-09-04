package com.ailearning.platform.analytics.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
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
            AnalyticsKafkaConsumerProperties properties
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(properties.retryDelay().toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS)
        ));
        return factory;
    }
}
