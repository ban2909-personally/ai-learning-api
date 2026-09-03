package com.ailearning.platform.mentoring;

import com.ailearning.platform.learning.api.usecase.mentoring.MentoringLessonContextLookup;
import com.ailearning.platform.mentoring.adapter.out.persistence.jpa.repository.MentorConversationJpaRepository;
import com.ailearning.platform.mentoring.adapter.out.persistence.jpa.repository.MentorMessageJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;

@ApplicationModuleTest
@Import(MentoringModuleIntegrationTest.TestMetricsConfiguration.class)
@TestPropertySource(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
class MentoringModuleIntegrationTest {
    @MockitoBean MentoringLessonContextLookup lessonContexts;
    @MockitoBean MentorConversationJpaRepository conversations;
    @MockitoBean MentorMessageJpaRepository messages;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean Clock clock;

    @Test
    void moduleBootstrapsInIsolation() {
    }

    @TestConfiguration
    static class TestMetricsConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
