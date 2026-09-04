package com.ailearning.platform.analytics.adapter.out.persistence;

import com.ailearning.platform.analytics.domain.model.CompletionFact;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class CompletionAnalyticsPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final UUID FIRST_USER = UUID.randomUUID();
    private static final UUID SECOND_USER = UUID.randomUUID();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_analytics_test")
            .withUsername("test")
            .withPassword("test");

    private static CompletionAnalyticsPersistenceAdapter analytics;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        analytics = new CompletionAnalyticsPersistenceAdapter(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM learning_completion_facts");
    }

    @Test
    void deduplicatesByEventAndSemanticLessonCompletion() {
        UUID enrollmentId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        var original = fact(UUID.randomUUID(), FIRST_USER, enrollmentId, UUID.randomUUID(), lessonId, NOW);
        var duplicateEvent = fact(original.eventId(), SECOND_USER, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);
        var duplicateCompletion = fact(UUID.randomUUID(), FIRST_USER, enrollmentId, original.courseId(), lessonId, NOW);

        assertThat(analytics.append(original)).isTrue();
        assertThat(analytics.append(duplicateEvent)).isFalse();
        assertThat(analytics.append(duplicateCompletion)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_completion_facts", Integer.class)).isOne();
    }

    @Test
    void summarizesOnlyTheRequestedUserAndKeepsGlobalTotalsWhenCoursesAreLimited() {
        UUID recentCourse = UUID.randomUUID();
        UUID olderCourse = UUID.randomUUID();
        analytics.append(fact(UUID.randomUUID(), FIRST_USER, UUID.randomUUID(), recentCourse, UUID.randomUUID(), NOW));
        analytics.append(fact(UUID.randomUUID(), FIRST_USER, UUID.randomUUID(), recentCourse, UUID.randomUUID(), NOW.minusSeconds(1)));
        analytics.append(fact(UUID.randomUUID(), FIRST_USER, UUID.randomUUID(), olderCourse, UUID.randomUUID(), NOW.minusSeconds(10)));
        analytics.append(fact(UUID.randomUUID(), SECOND_USER, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(1)));

        var snapshot = analytics.summarize(FIRST_USER, 1);

        assertThat(snapshot.completedLessons()).isEqualTo(3);
        assertThat(snapshot.coursesWithCompletions()).isEqualTo(2);
        assertThat(snapshot.lastCompletedAt()).isEqualTo(NOW);
        assertThat(snapshot.courses()).singleElement().satisfies(course -> {
            assertThat(course.courseId()).isEqualTo(recentCourse);
            assertThat(course.completedLessons()).isEqualTo(2);
        });
        assertThat(analytics.summarize(UUID.randomUUID(), 10))
                .isEqualTo(com.ailearning.platform.analytics.application.port.out.CompletionAnalyticsSnapshot.empty());
    }

    private CompletionFact fact(
            UUID eventId,
            UUID userId,
            UUID enrollmentId,
            UUID courseId,
            UUID lessonId,
            Instant completedAt
    ) {
        return new CompletionFact(
                eventId,
                userId,
                enrollmentId,
                courseId,
                lessonId,
                completedAt,
                completedAt.plusSeconds(30)
        );
    }
}
