package com.ailearning.platform.notification.adapter.in.websocket.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class StompJwtAuthenticationInterceptor implements ChannelInterceptor {
    static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_BEARER_TOKEN_LENGTH = 4096;

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter authenticationConverter;
    private final ExpiringWebSocketSessions sessions;
    private final Clock clock;

    public StompJwtAuthenticationInterceptor(
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter authenticationConverter,
            ExpiringWebSocketSessions sessions,
            Clock clock
    ) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        Jwt jwt = decode(singleBearerToken(accessor));
        requireUuidSubject(jwt.getSubject());
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            throw invalidCredentials();
        }

        var authentication = authenticationConverter.convert(jwt);
        if (authentication == null || !authentication.isAuthenticated()) {
            throw invalidCredentials();
        }
        accessor.setUser(authentication);
        sessions.expireAt(accessor.getSessionId(), expiresAt);
        return message;
    }

    private String singleBearerToken(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader(AUTHORIZATION_HEADER);
        if (values == null || values.size() != 1) {
            throw invalidCredentials();
        }
        String value = values.getFirst();
        if (!value.startsWith(BEARER_PREFIX)
                || value.length() <= BEARER_PREFIX.length()
                || value.length() > MAX_BEARER_TOKEN_LENGTH) {
            throw invalidCredentials();
        }
        return value.substring(BEARER_PREFIX.length());
    }

    private Jwt decode(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException exception) {
            throw invalidCredentials();
        }
    }

    private void requireUuidSubject(String subject) {
        try {
            UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw invalidCredentials();
        }
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Invalid notification WebSocket credentials");
    }
}
