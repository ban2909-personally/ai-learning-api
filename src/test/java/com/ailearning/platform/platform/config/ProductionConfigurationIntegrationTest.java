package com.ailearning.platform.platform.config;

import com.ailearning.platform.platform.security.CorsProperties;
import com.ailearning.platform.platform.security.SecurityProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class ProductionConfigurationIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_production_configuration_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void productionProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", () -> "redis.internal");
        registry.add("spring.data.redis.password", () -> "test-only-redis-password");
        registry.add("spring.kafka.bootstrap-servers", () -> "kafka.internal:9092");
        registry.add("app.security.jwt-secret",
                () -> "cHJvZHVjdGlvbi1jb25maWd1cmF0aW9uLXRlc3Qtc2VjcmV0LTMyaA==");
        registry.add("app.security.issuer", () -> "production-configuration-test");
        registry.add("app.cors.allowed-origin", () -> "https://learning.example.test");
        registry.add("app.ai.openai.api-key", () -> "test-only-openai-key");
        registry.add("app.storage.minio.endpoint", () -> "https://minio.internal");
        registry.add("app.storage.minio.access-key", () -> "test-only-minio-access");
        registry.add("app.storage.minio.secret-key", () -> "test-only-minio-secret");
        registry.add("app.storage.minio.bucket", () -> "lesson-media-test");
    }

    @Autowired Environment environment;
    @Autowired DataSource dataSource;
    @Autowired SecurityProperties security;
    @Autowired CorsProperties cors;

    @Test
    void activatesFailClosedSecurityAndBoundedRuntimeDefaults() {
        assertThat(environment.matchesProfiles("prod")).isTrue();
        assertThat(environment.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(environment.getProperty("management.endpoint.health.group.liveness.include"))
                .isEqualTo("livenessState,ping");
        assertThat(environment.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db");
        assertThat(security.refreshCookieSecure()).isTrue();
        assertThat(security.issuer()).isEqualTo("production-configuration-test");
        assertThat(cors.allowedOrigin()).isEqualTo("https://learning.example.test");
        assertThat(dataSource).isInstanceOfSatisfying(HikariDataSource.class, hikari -> {
            assertThat(hikari.getMaximumPoolSize()).isEqualTo(20);
            assertThat(hikari.getMinimumIdle()).isEqualTo(5);
            assertThat(hikari.getConnectionTimeout()).isEqualTo(5_000);
            assertThat(hikari.getValidationTimeout()).isEqualTo(3_000);
        });
    }
}
