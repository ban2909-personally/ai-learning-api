package com.ailearning.platform.assessment.api;

import static org.assertj.core.api.Assertions.*;

import com.ailearning.platform.assessment.api.contract.ExamDraftContent;
import com.ailearning.platform.assessment.api.usecase.ExamAuthoringUseCase;
import com.ailearning.platform.assessment.application.port.out.ExamAuthoringStore;
import com.ailearning.platform.assessment.domain.enumtype.PracticeSkill;
import com.ailearning.platform.assessment.domain.enumtype.QuestionKind;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ExamAuthoringConcurrencyTest {
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
    @Autowired ExamAuthoringStore store;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.ailearning.platform.catalog.application.port.out.PopularCatalogCache catalogCache;

    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void user() {
        jdbc.update(
                "INSERT INTO users(id,email,password_hash,display_name,status) VALUES"
                    + " (?,?,?,'Test','ACTIVE')",
                admin,
                admin + "@example.invalid",
                "unused");
        jdbc.update(
                "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='ADMIN'",
                admin);
    }

    private ExamDraftContent content() {
        return new ExamDraftContent(
                "English email",
                "",
                15,
                List.of(
                        new ExamDraftContent.SectionInput(
                                PracticeSkill.READING,
                                "Reading",
                                "An email.",
                                null,
                                List.of(
                                        new ExamDraftContent.QuestionInput(
                                                QuestionKind.TEXT,
                                                "What is the topic?",
                                                List.of(),
                                                "Meeting",
                                                "The email is about a meeting.")))));
    }

    @Test
    void concurrentSavesWithSameVersionHaveExactlyOneWinner() throws Exception {
        var draft = authoring.create(admin, "save-" + UUID.randomUUID(), "English email", "", 15);
        assertSingleWinner(() -> authoring.save(admin, draft.id(), draft.version(), content()));
        assertThat(authoring.workspace(admin, draft.id()).version()).isEqualTo(1);
        assertThat(authoring.workspace(admin, draft.id()).exam().sections()).hasSize(1);
    }

    @Test
    void concurrentCloneCreatesOnlyOneEditableRevision() throws Exception {
        var draft = authoring.create(admin, "clone-" + UUID.randomUUID(), "English email", "", 15);
        draft = authoring.save(admin, draft.id(), draft.version(), content());
        draft = authoring.submit(admin, draft.id(), draft.version());
        var published = authoring.publish(admin, draft.id(), draft.version());
        assertSingleWinner(
                () -> authoring.clonePublished(admin, published.id(), published.version()));
        assertThat(
                        jdbc.queryForObject(
                                """
SELECT count(*) FROM practice_exams WHERE series_id=? AND status='DRAFT'
""",
                                Integer.class,
                                published.seriesId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                """
SELECT count(*) FROM practice_exams WHERE series_id=? AND status='PUBLISHED'
""",
                                Integer.class,
                                published.seriesId()))
                .isEqualTo(1);
    }

    @Test
    void failedPersistenceSaveRollsBackVersionAndDeletedContent() {
        var draft =
                authoring.create(admin, "rollback-" + UUID.randomUUID(), "English email", "", 15);
        draft = authoring.save(admin, draft.id(), draft.version(), content());
        var before = draft;
        var section = before.exam().sections().getFirst();
        var question = section.questions().getFirst();
        var invalid =
                new PracticeExam(
                        before.id(),
                        before.exam().slug(),
                        "Changed title",
                        "",
                        15,
                        List.of(
                                new PracticeExam.Section(
                                        section.id(),
                                        section.skill(),
                                        section.title(),
                                        section.passage(),
                                        null,
                                        List.of(
                                                new PracticeExam.Question(
                                                        question.id(),
                                                        question.kind(),
                                                        null,
                                                        question.options(),
                                                        question.correctAnswer(),
                                                        question.explanation())))));
        assertThatThrownBy(() -> store.save(before.id(), before.version(), invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(authoring.workspace(admin, before.id())).isEqualTo(before);
    }

    @Test
    void failedPublicationRollsBackArchivingThePreviousRevision() {
        var draft =
                authoring.create(admin, "publish-" + UUID.randomUUID(), "English email", "", 15);
        draft = authoring.save(admin, draft.id(), draft.version(), content());
        draft = authoring.submit(admin, draft.id(), draft.version());
        var published = authoring.publish(admin, draft.id(), draft.version());
        var next = authoring.clonePublished(admin, published.id(), published.version());
        var pending = authoring.submit(admin, next.id(), next.version());
        assertThatThrownBy(() -> store.publish(pending.id(), pending.version(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(authoring.workspace(admin, published.id())).isEqualTo(published);
        assertThat(authoring.workspace(admin, pending.id())).isEqualTo(pending);
    }

    private void assertSingleWinner(Supplier<?> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks =
                    List.of(0, 1).stream()
                            .map(
                                    index ->
                                            executor.submit(
                                                    () -> {
                                                        ready.countDown();
                                                        if (!start.await(10, TimeUnit.SECONDS))
                                                            throw new IllegalStateException(
                                                                    "Start timeout");
                                                        try {
                                                            action.get();
                                                            return true;
                                                        } catch (BusinessException conflict) {
                                                            assertThat(conflict.code())
                                                                    .isEqualTo(
                                                                            "exam_revision_conflict");
                                                            return false;
                                                        }
                                                    }))
                            .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int winners = 0;
            for (var task : tasks) if (task.get(20, TimeUnit.SECONDS)) winners++;
            assertThat(winners).isEqualTo(1);
        }
    }
}
