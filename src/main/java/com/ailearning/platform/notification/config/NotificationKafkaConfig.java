package com.ailearning.platform.notification.config;

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
@ConditionalOnProperty(prefix = "app.notifications.consumer", name = "enabled", havingValue = "true")
public class NotificationKafkaConfig {
    public static final String CONTAINER_FACTORY = "notificationKafkaListenerContainerFactory";

    @Bean(name = CONTAINER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<String, String> notificationKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            NotificationKafkaConsumerProperties properties
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(errorHandler(properties));
        return factory;
    }

    private DefaultErrorHandler errorHandler(NotificationKafkaConsumerProperties properties) {
        long delayMillis = properties.retryDelay().toMillis();
        return new DefaultErrorHandler(new FixedBackOff(delayMillis, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
