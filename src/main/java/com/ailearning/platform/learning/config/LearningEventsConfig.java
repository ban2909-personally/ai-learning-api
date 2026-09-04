package com.ailearning.platform.learning.config;

import com.ailearning.platform.learning.api.usecase.DispatchLearningEventsUseCase;
import com.ailearning.platform.learning.application.port.out.LearningEventBroker;
import com.ailearning.platform.learning.application.port.out.LearningEventDispatchMonitor;
import com.ailearning.platform.learning.application.port.out.LearningEventOutbox;
import com.ailearning.platform.learning.application.service.impl.LearningEventDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.UUID;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.messaging.learning-events", name = "enabled", havingValue = "true")
public class LearningEventsConfig {
    @Bean
    DispatchLearningEventsUseCase dispatchLearningEventsUseCase(
            LearningEventOutbox outbox,
            LearningEventBroker broker,
            LearningEventDispatchMonitor monitor,
            LearningEventsProperties properties,
            Clock clock
    ) {
        return new LearningEventDispatcher(
                outbox,
                broker,
                monitor,
                clock,
                properties.batchSize(),
                properties.claimLease(),
                properties.retryInitial(),
                properties.retryMax(),
                UUID.randomUUID().toString()
        );
    }
}
