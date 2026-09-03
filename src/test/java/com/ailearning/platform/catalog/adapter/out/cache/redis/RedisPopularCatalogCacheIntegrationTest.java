package com.ailearning.platform.catalog.adapter.out.cache.redis;

import com.ailearning.platform.catalog.config.PopularCatalogCacheProperties;
import com.ailearning.platform.catalog.domain.enums.CourseLevel;
import com.ailearning.platform.catalog.domain.model.Category;
import com.ailearning.platform.catalog.domain.model.Course;
import com.ailearning.platform.sharedkernel.pagination.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisPopularCatalogCacheIntegrationTest {
    private static final String KEY_PREFIX = "test:catalog:popular:v1";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisPopularCatalogCache cache;
    private static SimpleMeterRegistry meterRegistry;

    @BeforeAll
    static void setUpClient() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        meterRegistry = new SimpleMeterRegistry();
        cache = new RedisPopularCatalogCache(
                redis,
                objectMapper,
                new PopularCatalogCacheProperties(KEY_PREFIX, Duration.ofSeconds(30)),
                meterRegistry
        );
    }

    @AfterAll
    static void closeClient() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void storesJsonWithDeterministicKeyAndBoundedTtl() {
        PageResult<Course> expected = page();

        cache.put(12, expected);

        assertThat(cache.find(12)).contains(expected);
        String key = KEY_PREFIX + ":size:12";
        assertThat(redis.opsForValue().get(key)).startsWith("{").contains("spring-clean");
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 30L);
        assertThat(counter("hit")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void discardsCorruptJsonAndTreatsItAsMiss() {
        String key = KEY_PREFIX + ":size:24";
        redis.opsForValue().set(key, "{not-json", Duration.ofSeconds(30));

        assertThat(cache.find(24)).isEmpty();
        assertThat(redis.hasKey(key)).isFalse();
        assertThat(counter("failure")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void recordsCacheMisses() {
        assertThat(cache.find(50)).isEmpty();
        assertThat(counter("miss")).isGreaterThanOrEqualTo(1);
    }

    private static double counter(String result) {
        return meterRegistry.get("catalog.cache.access").tag("result", result).counter().count();
    }

    private static PageResult<Course> page() {
        Category category = new Category(
                UUID.fromString("7b0ca183-3f1f-4aa2-a280-e9e3bacf70ee"),
                "backend",
                "Backend",
                "Backend courses"
        );
        Course course = new Course(
                UUID.fromString("0978ff6c-d7b7-4bf5-a37c-bb6f4a99909d"),
                "spring-clean",
                "Spring Clean Architecture",
                "Build maintainable APIs",
                "Course description",
                CourseLevel.INTERMEDIATE,
                "vi",
                new BigDecimal("499000"),
                "VND",
                null,
                600,
                category,
                UUID.fromString("2503b194-e348-44a8-97dd-7814647552ca"),
                "Instructor",
                Instant.parse("2026-09-03T00:00:00Z")
        );
        return new PageResult<>(List.of(course), 0, 12, 1, 1);
    }
}
