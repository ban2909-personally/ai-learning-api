package com.ailearning.platform.notification.adapter.out.messaging.websocket;

import com.ailearning.platform.notification.api.contract.NotificationView;
import com.ailearning.platform.notification.domain.enums.NotificationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StompNotificationRealtimeDeliveryTest {
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final StompNotificationRealtimeDelivery delivery =
            new StompNotificationRealtimeDelivery(messaging, registry);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deliversOnlyAfterTheProjectionTransactionCommits() {
        NotificationView notification = notification();
        TransactionSynchronizationManager.initSynchronization();

        delivery.publish(notification);

        verify(messaging, never()).convertAndSendToUser(any(), any(), any());
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
        verify(messaging).convertAndSendToUser(
                notification.recipientId().toString(),
                StompNotificationRealtimeDelivery.USER_DESTINATION,
                RealtimeNotificationMessage.from(notification)
        );
        assertThat(registry.counter("notifications.realtime.delivery", "outcome", "sent").count())
                .isEqualTo(1);
    }

    @Test
    void requiresATransactionSoRealtimeCannotPrecedePersistence() {
        assertThatThrownBy(() -> delivery.publish(notification()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    private NotificationView notification() {
        return new NotificationView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationType.LESSON_COMPLETED,
                "Hoàn thành bài học",
                "Nội dung",
                "/my-learning",
                Instant.parse("2026-09-04T10:00:00Z"),
                null
        );
    }
}
