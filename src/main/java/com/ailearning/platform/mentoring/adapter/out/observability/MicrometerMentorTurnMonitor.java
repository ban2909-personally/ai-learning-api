package com.ailearning.platform.mentoring.adapter.out.observability;

import com.ailearning.platform.mentoring.application.port.out.MentorTurnMonitor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MicrometerMentorTurnMonitor implements MentorTurnMonitor {
    private final MeterRegistry registry;

    public MicrometerMentorTurnMonitor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void accepted() {
        counter("accepted", "none").increment();
    }

    @Override
    public void rejected(String reason) {
        counter("rejected", reason).increment();
    }

    @Override
    public void completed() {
        counter("completed", "none").increment();
    }

    @Override
    public void failed(String reason) {
        counter("failed", reason).increment();
    }

    private Counter counter(String outcome, String reason) {
        return Counter.builder("mentor.turns")
                .description("AI Mentor turn outcomes")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(registry);
    }
}
