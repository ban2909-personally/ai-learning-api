package com.ailearning.platform.notification;

import com.ailearning.platform.notification.adapter.in.websocket.security.StompJwtAuthenticationInterceptor;
import com.ailearning.platform.notification.application.port.out.NotificationRealtimeDelivery;
import com.ailearning.platform.platform.security.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest
@TestPropertySource(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
class NotificationModuleIntegrationTest {
    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private NotificationRealtimeDelivery realtime;

    @MockitoBean
    private CorsProperties corsProperties;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    @Qualifier("clientInboundChannel")
    private AbstractSubscribableChannel clientInboundChannel;

    @Test
    void moduleBootstrapsInIsolation() {
    }

    @Test
    void authenticatesThenPublishesTheSecurityContextBeforeAuthorization() {
        List<Class<?>> interceptorTypes = clientInboundChannel.getInterceptors().stream()
                .map(Object::getClass)
                .toList();

        assertThat(interceptorTypes).containsSubsequence(
                StompJwtAuthenticationInterceptor.class,
                SecurityContextChannelInterceptor.class,
                AuthorizationChannelInterceptor.class
        );
    }
}
