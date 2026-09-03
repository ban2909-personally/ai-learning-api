package com.ailearning.platform.mentoring.adapter.out.cache.redis;

import com.ailearning.platform.mentoring.config.MentorQuotaProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisMentorQuotaIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisMentorQuota quota;

    @BeforeAll
    static void setUpClient() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        quota = new RedisMentorQuota(
                redis,
                new MentorQuotaProperties("test:mentor:quota:v1", 2, Duration.ofSeconds(30))
        );
    }

    @AfterAll
    static void closeClient() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void consumesQuotaAtomicallyAndAppliesTtl() {
        UUID userId = UUID.randomUUID();

        assertThat(quota.consume(userId)).satisfies(decision -> {
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isEqualTo(1);
        });
        assertThat(quota.consume(userId)).satisfies(decision -> {
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isZero();
        });
        assertThat(quota.consume(userId).allowed()).isFalse();
        assertThat(redis.getExpire("test:mentor:quota:v1:user:" + userId, TimeUnit.SECONDS))
                .isBetween(1L, 30L);
    }
}

