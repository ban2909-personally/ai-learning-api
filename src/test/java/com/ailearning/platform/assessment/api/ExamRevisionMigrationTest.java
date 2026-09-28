package com.ailearning.platform.assessment.api;

import static org.assertj.core.api.Assertions.*;

import com.ailearning.platform.assessment.api.contract.ExamDraftContent;
import com.ailearning.platform.assessment.api.usecase.ExamAuthoringUseCase;
import com.ailearning.platform.assessment.api.usecase.PracticeUseCase;
import com.ailearning.platform.assessment.domain.enumtype.PracticeSkill;
import com.ailearning.platform.assessment.domain.enumtype.QuestionKind;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@SpringBootTest(properties = "spring.flyway.target=17")
@Testcontainers(disabledWithoutDocker = true)
class ExamRevisionMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ExamAuthoringUseCase authoring;
    @Autowired PracticeUseCase practice;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.ailearning.platform.catalog.application.port.out.PopularCatalogCache catalogCache;

    @Test
    void upgradePreservesLegacyIdsAnswersAndWritingReviewAfterReplacementPublication() {
        UUID student = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID attempt = UUID.randomUUID();
        UUID exam = UUID.fromString("31111111-1111-4111-8111-111111111111");
        UUID choice = UUID.fromString("33333333-3333-4333-8333-333333333331");
        UUID writing = UUID.fromString("33333333-3333-4333-8333-333333333335");
        user(student, "STUDENT");
        user(admin, "ADMIN");
        jdbc.update(
                "INSERT INTO practice_attempts(id,exam_id,owner_id,status,submitted_at) VALUES"
                    + " (?,?,?,'SUBMITTED',CURRENT_TIMESTAMP)",
                attempt,
                exam,
                student);
        jdbc.update(
                "INSERT INTO practice_answers(attempt_id,question_id,answer) VALUES"
                    + " (?,?,?),(?,?,?)",
                attempt,
                choice,
                "Tuesday at 9:30",
                attempt,
                writing,
                "I propose a workshop on workplace email.");
        jdbc.update(
                """
INSERT INTO practice_writing_reviews(attempt_id,question_id,reviewer_id,task_score,
    coherence_score,vocabulary_score,grammar_score,feedback) VALUES (?,?,?,4,3,4,5,'Clear purpose.')
""",
                attempt,
                writing,
                admin);

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        var before = practice.result(student, attempt);
        assertThat(before.correct()).isEqualTo(1);
        assertThat(before.sections().get(2).questions().getFirst().writingFeedback().totalScore())
                .isEqualTo(16);
        var original = authoring.workspace(admin, exam);
        assertThat(original.revision()).isEqualTo(1);
        var draft = authoring.clonePublished(admin, exam, original.version());
        UUID changedQuestion = draft.exam().sections().getFirst().questions().getFirst().id();
        var content =
                new ExamDraftContent(
                        draft.exam().title(),
                        "Replacement description",
                        draft.exam().durationMinutes(),
                        draft.exam().sections().stream()
                                .map(
                                        s ->
                                                new ExamDraftContent.SectionInput(
                                                        PracticeSkill.valueOf(s.skill()),
                                                        s.title(),
                                                        s.passage(),
                                                        s.audioText(),
                                                        s.questions().stream()
                                                                .map(
                                                                        q ->
                                                                                new ExamDraftContent
                                                                                        .QuestionInput(
                                                                                        QuestionKind
                                                                                                .valueOf(
                                                                                                        q
                                                                                                                .kind()),
                                                                                        q.prompt(),
                                                                                        q.options(),
                                                                                        q.id().equals(
                                                                                                                changedQuestion)
                                                                                                ? "Monday"
                                                                                                      + " at 9:30"
                                                                                                : q
                                                                                                        .correctAnswer(),
                                                                                        q
                                                                                                .explanation()))
                                                                .toList()))
                                .toList());
        draft = authoring.save(admin, draft.id(), draft.version(), content);
        draft = authoring.submit(admin, draft.id(), draft.version());
        authoring.publish(admin, draft.id(), draft.version());
        assertThat(practice.result(student, attempt)).isEqualTo(before);
        assertThat(practice.attempt(student, attempt).examId()).isEqualTo(exam);
        assertThat(practice.exam("english-workplace-starter").id()).isEqualTo(draft.id());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM practice_exams WHERE id=?", String.class, exam))
                .isEqualTo("ARCHIVED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM practice_answers WHERE attempt_id=?",
                                Integer.class,
                                attempt))
                .isEqualTo(2);
    }

    private void user(UUID id, String role) {
        jdbc.update(
                "INSERT INTO users(id,email,password_hash,display_name,status) VALUES"
                    + " (?,?,?,'Test','ACTIVE')",
                id,
                id + "@example.invalid",
                "unused");
        jdbc.update(
                "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?",
                id,
                role);
    }
}
