package com.ailearning.platform.notification.config;

import com.ailearning.platform.notification.adapter.in.websocket.security.ExpiringWebSocketSessions;
import com.ailearning.platform.notification.adapter.in.websocket.security.StompJwtAuthenticationInterceptor;
import com.ailearning.platform.notification.adapter.out.messaging.websocket.StompNotificationRealtimeDelivery;
import com.ailearning.platform.notification.application.port.out.NotificationRealtimeDelivery;
import com.ailearning.platform.platform.security.CorsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.time.Clock;

@Configuration
@EnableWebSocketMessageBroker
public class NotificationWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    public static final String ENDPOINT = "/ws/notifications";
    public static final String SUBSCRIPTION_DESTINATION = "/user/queue/notifications";

    private final NotificationWebSocketProperties properties;
    private final CorsProperties cors;
    private final ThreadPoolTaskScheduler scheduler;
    private final ExpiringWebSocketSessions sessions;
    private final StompJwtAuthenticationInterceptor authentication;
    private final AuthorizationChannelInterceptor authorization;

    public NotificationWebSocketConfig(
            NotificationWebSocketProperties properties,
            CorsProperties cors,
            @Qualifier("notificationWebSocketTaskScheduler") ThreadPoolTaskScheduler scheduler,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter authenticationConverter,
            Clock clock
    ) {
        this.properties = properties;
        this.cors = cors;
        this.scheduler = scheduler;
        this.sessions = new ExpiringWebSocketSessions(scheduler);
        this.authentication = new StompJwtAuthenticationInterceptor(
                jwtDecoder,
                authenticationConverter,
                sessions,
                clock
        );
        this.authorization = new AuthorizationChannelInterceptor(messageAuthorizationManager());
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT).setAllowedOrigins(cors.allowedOrigin());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{properties.heartbeat().toMillis(), properties.heartbeat().toMillis()})
                .setTaskScheduler(scheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authentication, authorization);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(properties.messageSizeBytes());
        registry.setSendTimeLimit(Math.toIntExact(properties.sendTimeLimit().toMillis()));
        registry.setSendBufferSizeLimit(properties.sendBufferSizeBytes());
        registry.setTimeToFirstMessage(Math.toIntExact(properties.firstMessageTimeout().toMillis()));
        registry.addDecoratorFactory(sessions.decoratorFactory());
    }

    @Bean(name = "notificationWebSocketTaskScheduler", defaultCandidate = false)
    static ThreadPoolTaskScheduler notificationWebSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("notification-ws-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    static AuthorizationManager<Message<?>> messageAuthorizationManager() {
        return MessageMatcherDelegatingAuthorizationManager.builder()
                .nullDestMatcher().authenticated()
                .simpSubscribeDestMatchers(SUBSCRIPTION_DESTINATION).authenticated()
                .simpTypeMatchers(SimpMessageType.MESSAGE, SimpMessageType.SUBSCRIBE).denyAll()
                .anyMessage().denyAll()
                .build();
    }

    @Bean
    NotificationRealtimeDelivery notificationRealtimeDelivery(
            org.springframework.messaging.simp.SimpMessagingTemplate messaging,
            MeterRegistry registry
    ) {
        return new StompNotificationRealtimeDelivery(messaging, registry);
    }
}
