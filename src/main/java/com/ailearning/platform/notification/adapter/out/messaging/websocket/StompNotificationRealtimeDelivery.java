package com.ailearning.platform.notification.adapter.out.messaging.websocket;

import com.ailearning.platform.notification.api.contract.NotificationView;
import com.ailearning.platform.notification.application.port.out.NotificationRealtimeDelivery;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class StompNotificationRealtimeDelivery implements NotificationRealtimeDelivery {
    public static final String USER_DESTINATION = "/queue/notifications";
    private static final Logger log = LoggerFactory.getLogger(StompNotificationRealtimeDelivery.class);

    private final SimpMessagingTemplate messaging;
    private final Counter sent;
    private final Counter failed;

    public StompNotificationRealtimeDelivery(SimpMessagingTemplate messaging, MeterRegistry registry) {
        this.messaging = messaging;
        this.sent = counter(registry, "sent");
        this.failed = counter(registry, "failed");
    }

    @Override
    public void publish(NotificationView notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Realtime notification delivery requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(notification);
            }
        });
    }

    private void send(NotificationView notification) {
        try {
            messaging.convertAndSendToUser(
                    notification.recipientId().toString(),
                    USER_DESTINATION,
                    RealtimeNotificationMessage.from(notification)
            );
            sent.increment();
        } catch (RuntimeException exception) {
            failed.increment();
            log.warn("Realtime notification delivery failed for event {}", notification.id(), exception);
        }
    }

    private Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("notifications.realtime.delivery")
                .description("Notification realtime delivery outcomes")
                .tag("outcome", outcome)
                .register(registry);
    }
}
