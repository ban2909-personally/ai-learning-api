package com.ailearning.platform.mentoring.adapter.out.persistence;

import com.ailearning.platform.mentoring.application.port.out.MentorConversationStore;
import com.ailearning.platform.mentoring.domain.enums.MentorMessageRole;
import com.ailearning.platform.mentoring.domain.model.MentorMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/mentor-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/mentor-test-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MentorConversationPersistenceIntegrationTest {
    private static final UUID USER_ID = UUID.fromString("18111111-1111-4111-8111-111111111111");
    private static final UUID COURSE_ID = UUID.fromString("38333333-3333-4333-8333-333333333333");
    private static final UUID LESSON_ID = UUID.fromString("58555555-5555-4555-8555-555555555555");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("ai_learning_mentor_persistence_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MentorConversationStore store;

    @Test
    void createsOneConversationAndReturnsBoundedChronologicalHistory() {
        Instant firstAt = Instant.parse("2026-09-03T10:00:00Z");
        var first = store.findOrCreate(
                UUID.randomUUID(), USER_ID, COURSE_ID, LESSON_ID, firstAt
        );
        var same = store.findOrCreate(
                UUID.randomUUID(), USER_ID, COURSE_ID, LESSON_ID, firstAt.plusSeconds(1)
        );
        assertThat(same.id()).isEqualTo(first.id());

        store.append(new MentorMessage(
                UUID.randomUUID(), first.id(), MentorMessageRole.USER, "Question", null,
                null, null, firstAt.plusSeconds(2)
        ));
        store.append(new MentorMessage(
                UUID.randomUUID(), first.id(), MentorMessageRole.ASSISTANT, "Answer", "gpt-test",
                12, 2, firstAt.plusSeconds(3)
        ));

        assertThat(store.findRecentMessages(first.id(), 1))
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(MentorMessageRole.ASSISTANT);
                    assertThat(message.content()).isEqualTo("Answer");
                });
    }
}
