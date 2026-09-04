package com.ailearning.platform.learning.adapter.in.scheduling;

import com.ailearning.platform.learning.api.usecase.DispatchLearningEventsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.messaging.learning-events", name = "enabled", havingValue = "true")
public class LearningEventDispatchJob {
    private static final Logger log = LoggerFactory.getLogger(LearningEventDispatchJob.class);

    private final DispatchLearningEventsUseCase events;

    public LearningEventDispatchJob(DispatchLearningEventsUseCase events) {
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${app.messaging.learning-events.poll-delay:PT1S}")
    public void dispatchAvailable() {
        try {
            events.dispatchAvailable();
        } catch (RuntimeException exception) {
            log.warn("Learning-event dispatch cycle failed", exception);
        }
    }
}
