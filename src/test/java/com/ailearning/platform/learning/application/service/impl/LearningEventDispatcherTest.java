package com.ailearning.platform.learning.application.service.impl;

import com.ailearning.platform.learning.application.port.out.LearningEventBroker;
import com.ailearning.platform.learning.application.port.out.LearningEventDispatchMonitor;
import com.ailearning.platform.learning.application.port.out.LearningEventMessage;
import com.ailearning.platform.learning.application.port.out.LearningEventOutbox;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningEventDispatcherTest {
    private static final String OWNER = "worker-1";
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    private final LearningEventOutbox outbox = mock(LearningEventOutbox.class);
    private final LearningEventBroker broker = mock(LearningEventBroker.class);
    private final LearningEventDispatchMonitor monitor = mock(LearningEventDispatchMonitor.class);
    private final LearningEventDispatcher dispatcher = new LearningEventDispatcher(
            outbox,
            broker,
            monitor,
            Clock.fixed(NOW, ZoneOffset.UTC),
            10,
            Duration.ofMinutes(2),
            Duration.ofSeconds(2),
            Duration.ofMinutes(5),
            OWNER
    );

    @Test
    void claimsAndMarksAcknowledgedEventsPublished() {
        LearningEventMessage message = message(0);
        when(outbox.claimAvailable(OWNER, 10, NOW, NOW.plusSeconds(120)))
                .thenReturn(List.of(message));

        int published = dispatcher.dispatchAvailable();

        assertThat(published).isOne();
        var ordered = inOrder(broker, outbox, monitor);
        ordered.verify(broker).publish(message);
        ordered.verify(outbox).markPublished(message.eventId(), OWNER, NOW);
        ordered.verify(monitor).published();
    }

    @Test
    void reschedulesBrokerFailuresWithExponentialBackoff() {
        LearningEventMessage message = message(2);
        when(outbox.claimAvailable(OWNER, 10, NOW, NOW.plusSeconds(120)))
                .thenReturn(List.of(message));
        doThrow(new IllegalStateException("broker unavailable")).when(broker).publish(message);

        int published = dispatcher.dispatchAvailable();

        assertThat(published).isZero();
        verify(outbox).reschedule(
                message.eventId(),
                OWNER,
                NOW.plusSeconds(8),
                LearningEventDispatcher.BROKER_FAILURE_CODE
        );
        verify(outbox, never()).markPublished(message.eventId(), OWNER, NOW);
        verify(monitor).failed();
    }

    @Test
    void continuesTheBatchAfterAnIndividualPublishFailure() {
        LearningEventMessage failed = message(0);
        LearningEventMessage successful = message(0);
        when(outbox.claimAvailable(OWNER, 10, NOW, NOW.plusSeconds(120)))
                .thenReturn(List.of(failed, successful));
        doThrow(new IllegalStateException("broker unavailable")).when(broker).publish(failed);

        int published = dispatcher.dispatchAvailable();

        assertThat(published).isOne();
        verify(broker).publish(successful);
        verify(outbox).markPublished(successful.eventId(), OWNER, NOW);
    }

    @Test
    void capsRetryDelayAtConfiguredMaximum() {
        assertThat(dispatcher.retryDelay(20)).isEqualTo(Duration.ofMinutes(5));
    }

    private LearningEventMessage message(int attempts) {
        return new LearningEventMessage(
                UUID.randomUUID(),
                "lesson.completed",
                1,
                UUID.randomUUID().toString(),
                "{}",
                NOW,
                attempts
        );
    }
}
