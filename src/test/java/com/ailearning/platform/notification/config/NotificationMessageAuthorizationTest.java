package com.ailearning.platform.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.TestingAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessageAuthorizationTest {
    private final TestingAuthenticationToken authenticated =
            new TestingAuthenticationToken("user", "credentials", "ROLE_USER");

    @Test
    void permitsOnlyTheAuthenticatedNotificationSubscription() {
        assertThat(decision(SimpMessageType.SUBSCRIBE, NotificationWebSocketConfig.SUBSCRIPTION_DESTINATION))
                .isTrue();
        assertThat(decision(SimpMessageType.SUBSCRIBE, "/user/queue/another-user"))
                .isFalse();
        assertThat(decision(SimpMessageType.MESSAGE, "/app/notifications"))
                .isFalse();
    }

    private boolean decision(SimpMessageType type, String destination) {
        var manager = NotificationWebSocketConfig.messageAuthorizationManager();
        Message<byte[]> message = message(type, destination);
        return manager.authorize(() -> authenticated, message).isGranted();
    }

    private Message<byte[]> message(SimpMessageType type, String destination) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(type);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
