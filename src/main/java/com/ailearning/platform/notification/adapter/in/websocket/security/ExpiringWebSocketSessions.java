package com.ailearning.platform.notification.adapter.in.websocket.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExpiringWebSocketSessions {
    private static final Logger log = LoggerFactory.getLogger(ExpiringWebSocketSessions.class);
    private static final CloseStatus AUTHENTICATION_EXPIRED =
            CloseStatus.POLICY_VIOLATION.withReason("Authentication expired");

    private final TaskScheduler scheduler;
    private final ConcurrentMap<String, SessionLease> sessions = new ConcurrentHashMap<>();

    public ExpiringWebSocketSessions(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public WebSocketHandlerDecoratorFactory decoratorFactory() {
        return handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), new SessionLease(session, null));
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception exception) {
                    remove(session.getId());
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    public void expireAt(String sessionId, Instant expiresAt) {
        AtomicBoolean scheduled = new AtomicBoolean();
        sessions.computeIfPresent(sessionId, (id, current) -> {
            cancel(current.expiry());
            ScheduledFuture<?> expiry = java.util.Objects.requireNonNull(
                    scheduler.schedule(() -> closeExpired(id, current.session()), expiresAt),
                    "WebSocket expiry scheduling must be supported"
            );
            scheduled.set(true);
            return new SessionLease(current.session(), expiry);
        });
        if (!scheduled.get()) {
            throw new IllegalStateException("Notification WebSocket session is not registered");
        }
    }

    private void closeExpired(String sessionId, WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(AUTHENTICATION_EXPIRED);
            }
        } catch (IOException exception) {
            log.warn("Could not close expired notification WebSocket session {}", sessionId, exception);
        } finally {
            remove(sessionId);
        }
    }

    private void remove(String sessionId) {
        SessionLease removed = sessions.remove(sessionId);
        if (removed != null) {
            cancel(removed.expiry());
        }
    }

    private void cancel(ScheduledFuture<?> expiry) {
        if (expiry != null) {
            expiry.cancel(false);
        }
    }

    private record SessionLease(WebSocketSession session, ScheduledFuture<?> expiry) {
    }
}
