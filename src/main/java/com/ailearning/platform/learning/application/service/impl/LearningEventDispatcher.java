package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.learning.api.usecase.DispatchLearningEventsUseCase;
import com.ailearning.platform.learning.application.port.out.LearningEventBroker;
import com.ailearning.platform.learning.application.port.out.LearningEventDispatchMonitor;
import com.ailearning.platform.learning.application.port.out.LearningEventMessage;
import com.ailearning.platform.learning.application.port.out.LearningEventOutbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class LearningEventDispatcher implements DispatchLearningEventsUseCase {
    static final String BROKER_FAILURE_CODE = "broker_publish_failed";

    private final LearningEventOutbox outbox;
    private final LearningEventBroker broker;
    private final LearningEventDispatchMonitor monitor;
    private final Clock clock;
    private final int batchSize;
    private final Duration claimLease;
    private final Duration retryInitial;
    private final Duration retryMax;
    private final String owner;

    public LearningEventDispatcher(
            LearningEventOutbox outbox,
            LearningEventBroker broker,
            LearningEventDispatchMonitor monitor,
            Clock clock,
            int batchSize,
            Duration claimLease,
            Duration retryInitial,
            Duration retryMax,
            String owner
    ) {
        this.outbox = outbox;
        this.broker = broker;
        this.monitor = monitor;
        this.clock = clock;
        this.batchSize = batchSize;
        this.claimLease = claimLease;
        this.retryInitial = retryInitial;
        this.retryMax = retryMax;
        this.owner = owner;
    }

    @Override
    public int dispatchAvailable() {
        Instant claimedAt = clock.instant();
        var messages = outbox.claimAvailable(
                owner,
                batchSize,
                claimedAt,
                claimedAt.plus(claimLease)
        );
        int published = 0;
        for (LearningEventMessage message : messages) {
            if (publish(message)) {
                published++;
            }
        }
        return published;
    }

    private boolean publish(LearningEventMessage message) {
        try {
            broker.publish(message);
        } catch (RuntimeException exception) {
            Instant availableAt = clock.instant().plus(retryDelay(message.attempts()));
            outbox.reschedule(
                    message.eventId(),
                    owner,
                    availableAt,
                    BROKER_FAILURE_CODE
            );
            monitor.failed();
            return false;
        }

        outbox.markPublished(message.eventId(), owner, clock.instant());
        monitor.published();
        return true;
    }

    Duration retryDelay(int attempts) {
        int exponent = Math.min(attempts, 20);
        try {
            Duration candidate = retryInitial.multipliedBy(1L << exponent);
            return candidate.compareTo(retryMax) > 0 ? retryMax : candidate;
        } catch (ArithmeticException exception) {
            return retryMax;
        }
    }
}
