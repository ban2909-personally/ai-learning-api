package com.ailearning.platform.notification.adapter.in.websocket.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpiringWebSocketSessionsTest {
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ExpiringWebSocketSessions sessions = new ExpiringWebSocketSessions(scheduler);

    @Test
    void closesRegisteredSessionWhenAuthenticationExpires() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        WebSocketHandler delegate = mock(WebSocketHandler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        var handler = sessions.decoratorFactory().decorate(delegate);
        handler.afterConnectionEstablished(session);

        sessions.expireAt("session-1", Instant.parse("2026-09-04T10:05:00Z"));
        ArgumentCaptor<Runnable> expiry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(expiry.capture(), any(Instant.class));
        expiry.getValue().run();

        verify(session).close(org.mockito.ArgumentMatchers.argThat(status ->
                status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()
                        && "Authentication expired".equals(status.getReason())
        ));
        verify(future).cancel(false);
    }

    @Test
    void rejectsExpiryForAnUnknownSession() {
        assertThatThrownBy(() -> sessions.expireAt("unknown", Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not registered");
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
