package com.ailearning.platform.notification.adapter.in.websocket.security;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompJwtAuthenticationInterceptorTest {
    private static final Instant NOW = Instant.parse("2026-09-04T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final JwtAuthenticationConverter converter = mock(JwtAuthenticationConverter.class);
    private final ExpiringWebSocketSessions sessions = mock(ExpiringWebSocketSessions.class);
    private final StompJwtAuthenticationInterceptor interceptor = new StompJwtAuthenticationInterceptor(
            decoder,
            converter,
            sessions,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void authenticatesConnectAndExpiresTheSocketWithTheToken() {
        Instant expiresAt = NOW.plusSeconds(300);
        Jwt jwt = jwt(USER_ID.toString(), expiresAt);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        when(decoder.decode("valid-token")).thenReturn(jwt);
        when(converter.convert(jwt)).thenReturn(authentication);
        Message<byte[]> message = connect("session-1", "Bearer valid-token");

        Message<?> intercepted = interceptor.preSend(message, mock(MessageChannel.class));

        StompHeaderAccessor headers = StompHeaderAccessor.wrap(intercepted);
        assertThat(headers.getUser()).isSameAs(authentication);
        verify(sessions).expireAt("session-1", expiresAt);
    }

    @Test
    void rejectsMissingOrDuplicatedCredentials() {
        assertThatThrownBy(() -> interceptor.preSend(connect("session-1", null), mock(MessageChannel.class)))
                .isInstanceOf(BadCredentialsException.class);

        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionId("session-2");
        headers.addNativeHeader(StompJwtAuthenticationInterceptor.AUTHORIZATION_HEADER, "Bearer first");
        headers.addNativeHeader(StompJwtAuthenticationInterceptor.AUTHORIZATION_HEADER, "Bearer second");

        assertThatThrownBy(() -> interceptor.preSend(message(headers), mock(MessageChannel.class)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsExpiredTokenAndNonUuidSubject() {
        Jwt expired = jwt(USER_ID.toString(), NOW);
        when(decoder.decode("expired")).thenReturn(expired);
        assertThatThrownBy(() -> interceptor.preSend(
                connect("session-1", "Bearer expired"),
                mock(MessageChannel.class)
        )).isInstanceOf(BadCredentialsException.class);

        Jwt invalidSubject = jwt("not-a-user-id", NOW.plusSeconds(60));
        when(decoder.decode("bad-subject")).thenReturn(invalidSubject);
        assertThatThrownBy(() -> interceptor.preSend(
                connect("session-2", "Bearer bad-subject"),
                mock(MessageChannel.class)
        )).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void leavesNonConnectFramesForAuthorization() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        Message<byte[]> message = message(headers);

        assertThat(interceptor.preSend(message, mock(MessageChannel.class))).isSameAs(message);
    }

    private Message<byte[]> connect(String sessionId, String authorization) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionId(sessionId);
        if (authorization != null) {
            headers.addNativeHeader(StompJwtAuthenticationInterceptor.AUTHORIZATION_HEADER, authorization);
        }
        return message(headers);
    }

    private Message<byte[]> message(StompHeaderAccessor headers) {
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private Jwt jwt(String subject, Instant expiresAt) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(NOW.minusSeconds(60))
                .expiresAt(expiresAt)
                .build();
    }
}
