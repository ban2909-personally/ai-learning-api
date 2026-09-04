package com.ailearning.platform.learning.api;

import com.ailearning.platform.learning.api.usecase.LessonProgressUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.messaging.learning-events.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = {
        "/learning-event-test-cleanup.sql",
        "/learning-event-test-data.sql"
})
@Sql(
        scripts = "/learning-event-test-cleanup.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class LessonProgressOutboxTransactionIntegrationTest {
    private static final UUID USER_ID = UUID.fromString("71111111-1111-4111-8111-111111111111");
    private static final UUID LESSON_ID = UUID.fromString("75555555-5555-4555-8555-555555555555");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_progress_outbox_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    LessonProgressUseCase progress;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void progressAndOneCompletionEventCommitTogether() {
        progress.save(USER_ID, "learning-event-test-course", LESSON_ID, 300, true);
        var secondSave = progress.save(
                USER_ID,
                "learning-event-test-course",
                LESSON_ID,
                450,
                true
        );

        assertThat(secondSave.positionSeconds()).isEqualTo(450);
        assertThat(secondSave.completed()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM lesson_progress WHERE lesson_id = ? AND completed",
                Integer.class,
                LESSON_ID
        )).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_event_outbox WHERE event_type = 'lesson.completed'",
                Integer.class
        )).isOne();
    }

    @Test
    void outboxFailureRollsBackTheProgressTransition() {
        jdbc.execute("""
                ALTER TABLE learning_event_outbox
                ADD CONSTRAINT test_reject_lesson_completion
                CHECK (event_type <> 'lesson.completed')
                """);

        assertThatThrownBy(() -> progress.save(
                USER_ID,
                "learning-event-test-course",
                LESSON_ID,
                600,
                true
        )).isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM lesson_progress WHERE lesson_id = ?",
                Integer.class,
                LESSON_ID
        )).isZero();
    }
}
