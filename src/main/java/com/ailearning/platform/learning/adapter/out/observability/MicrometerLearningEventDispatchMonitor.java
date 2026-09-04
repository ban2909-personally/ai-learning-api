package com.ailearning.platform.learning.adapter.out.observability;

import com.ailearning.platform.learning.application.port.out.LearningEventDispatchMonitor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MicrometerLearningEventDispatchMonitor implements LearningEventDispatchMonitor {
    private final Counter published;
    private final Counter failed;

    public MicrometerLearningEventDispatchMonitor(MeterRegistry registry) {
        this.published = Counter.builder("learning.events.dispatch")
                .description("Learning integration-event dispatch outcomes")
                .tag("outcome", "published")
                .register(registry);
        this.failed = Counter.builder("learning.events.dispatch")
                .description("Learning integration-event dispatch outcomes")
                .tag("outcome", "failed")
                .register(registry);
    }

    @Override
    public void published() {
        published.increment();
    }

    @Override
    public void failed() {
        failed.increment();
    }
}
