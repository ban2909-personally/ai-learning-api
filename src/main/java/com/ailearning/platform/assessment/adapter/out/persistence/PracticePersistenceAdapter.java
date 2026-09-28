package com.ailearning.platform.assessment.adapter.out.persistence;

import com.ailearning.platform.assessment.application.port.out.ExamAuthoringStore;
import com.ailearning.platform.assessment.application.port.out.PracticeStore;
import com.ailearning.platform.assessment.domain.enumtype.ExamRevisionStatus;
import com.ailearning.platform.assessment.domain.model.ExamRevision;
import com.ailearning.platform.assessment.domain.model.ExamRevisionSummary;
import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.assessment.domain.model.PracticeExamSummary;
import com.ailearning.platform.assessment.domain.model.WritingReview;
import com.ailearning.platform.assessment.domain.model.WritingSubmission;
import com.ailearning.platform.assessment.domain.policy.ExamAuthoringPolicy;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PracticePersistenceAdapter implements PracticeStore, ExamAuthoringStore {
    private final JdbcTemplate jdbc;

    public PracticePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PracticeExamSummary> publishedExams() {
        return jdbc.query(
                """
                SELECT es.slug,e.title,e.description,e.duration_minutes,
                  ARRAY(SELECT skill FROM practice_sections WHERE exam_id=e.id
                        ORDER BY display_order) AS skills
                FROM practice_exams e JOIN practice_exam_series es ON es.id=e.series_id
                WHERE e.status='PUBLISHED' ORDER BY e.published_at DESC,e.id
                """,
                (rs, row) ->
                        new PracticeExamSummary(
                                rs.getString("slug"),
                                rs.getString("title"),
                                rs.getString("description"),
                                rs.getInt("duration_minutes"),
                                strings(rs.getArray("skills"))));
    }

    @Transactional(readOnly = true)
    public Optional<PracticeExam> publishedExam(String slug) {
        return jdbc
                .query(
                        """
                        SELECT e.id FROM practice_exams e
                        JOIN practice_exam_series es ON es.id=e.series_id
                        WHERE es.slug=? AND e.status='PUBLISHED'
                        """,
                        (rs, row) -> rs.getObject("id", UUID.class),
                        slug)
                .stream()
                .findFirst()
                .map(this::loadExam);
    }

    @Transactional(readOnly = true)
    public Optional<PracticeExam> attemptExam(UUID id) {
        return jdbc
                .query(
                        "SELECT id FROM practice_exams WHERE id=? AND status IN"
                            + " ('PUBLISHED','ARCHIVED')",
                        (rs, row) -> rs.getObject("id", UUID.class),
                        id)
                .stream()
                .findFirst()
                .map(this::loadExam);
    }

    private PracticeExam loadExam(UUID id) {
        return jdbc.query(
                        """
                        SELECT e.*,es.slug FROM practice_exams e
                        JOIN practice_exam_series es ON es.id=e.series_id WHERE e.id=?
                        """,
                        (rs, row) ->
                                new PracticeExam(
                                        id,
                                        rs.getString("slug"),
                                        rs.getString("title"),
                                        rs.getString("description"),
                                        rs.getInt("duration_minutes"),
                                        sections(id)),
                        id)
                .getFirst();
    }

    private List<PracticeExam.Section> sections(UUID examId) {
        return jdbc.query(
                "SELECT * FROM practice_sections WHERE exam_id=? ORDER BY display_order",
                (rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new PracticeExam.Section(
                            id,
                            rs.getString("skill"),
                            rs.getString("title"),
                            rs.getString("passage"),
                            rs.getString("audio_text"),
                            questions(id));
                },
                examId);
    }

    private List<PracticeExam.Question> questions(UUID sectionId) {
        return jdbc.query(
                "SELECT * FROM practice_questions WHERE section_id=? ORDER BY display_order",
                (rs, row) ->
                        new PracticeExam.Question(
                                rs.getObject("id", UUID.class), rs.getString("kind"),
                                rs.getString("prompt"), strings(rs.getArray("options")),
                                rs.getString("correct_answer"), rs.getString("explanation")),
                sectionId);
    }

    private static List<String> strings(Array array) throws SQLException {
        return Arrays.asList((String[]) array.getArray());
    }

    @Transactional
    public PracticeAttempt start(UUID examId, UUID ownerId) {
        UUID id = UUID.randomUUID();
        int inserted =
                jdbc.update(
                        """
WITH current_exam AS (
    SELECT id FROM practice_exams WHERE id=? AND status='PUBLISHED' FOR SHARE
)
INSERT INTO practice_attempts(id,exam_id,owner_id)
SELECT ?,id,? FROM current_exam
""",
                        examId,
                        id,
                        ownerId);
        if (inserted != 1) throw ExamAuthoringPolicy.conflict();
        return new PracticeAttempt(id, examId, ownerId, "IN_PROGRESS", Map.of());
    }

    @Transactional(readOnly = true)
    public Optional<PracticeAttempt> attempt(UUID attemptId, UUID ownerId) {
        return jdbc
                .query(
                        "SELECT * FROM practice_attempts WHERE id=? AND owner_id=?",
                        (rs, row) -> {
                            Map<UUID, String> answers = new HashMap<>();
                            jdbc.query(
                                    "SELECT question_id, answer FROM practice_answers WHERE"
                                        + " attempt_id=?",
                                    (org.springframework.jdbc.core.RowCallbackHandler)
                                            response -> {
                                                answers.put(
                                                        response.getObject(
                                                                "question_id", UUID.class),
                                                        response.getString("answer"));
                                            },
                                    attemptId);
                            return new PracticeAttempt(
                                    attemptId,
                                    rs.getObject("exam_id", UUID.class),
                                    ownerId,
                                    rs.getString("status"),
                                    answers);
                        },
                        attemptId,
                        ownerId)
                .stream()
                .findFirst();
    }

    @Transactional
    public boolean saveAnswer(UUID attemptId, UUID ownerId, UUID questionId, String answer) {
        boolean editable =
                !jdbc.query(
                                "SELECT id FROM practice_attempts WHERE id=? AND owner_id=? AND"
                                    + " status='IN_PROGRESS' FOR UPDATE",
                                (rs, row) -> rs.getObject("id", UUID.class),
                                attemptId,
                                ownerId)
                        .isEmpty();
        if (!editable) return false;
        jdbc.update(
                """
                INSERT INTO practice_answers(attempt_id,question_id,answer) VALUES (?,?,?)
                ON CONFLICT(attempt_id,question_id) DO UPDATE SET answer=EXCLUDED.answer
                """,
                attemptId,
                questionId,
                answer);
        return true;
    }

    @Transactional
    public boolean submit(UUID attemptId, UUID ownerId) {
        return jdbc.update(
                        """
UPDATE practice_attempts SET status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP
WHERE id=? AND owner_id=? AND status='IN_PROGRESS'
""",
                        attemptId,
                        ownerId)
                == 1;
    }

    @Transactional(readOnly = true)
    public Map<UUID, WritingReview> writingReviews(UUID attemptId) {
        Map<UUID, WritingReview> reviews = new HashMap<>();
        jdbc.query(
                "SELECT * FROM practice_writing_reviews WHERE attempt_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler)
                        row -> {
                            UUID questionId = row.getObject("question_id", UUID.class);
                            reviews.put(
                                    questionId,
                                    new WritingReview(
                                            attemptId,
                                            questionId,
                                            row.getObject("reviewer_id", UUID.class),
                                            row.getInt("task_score"),
                                            row.getInt("coherence_score"),
                                            row.getInt("vocabulary_score"),
                                            row.getInt("grammar_score"),
                                            row.getString("feedback")));
                        },
                attemptId);
        return Map.copyOf(reviews);
    }

    @Transactional(readOnly = true)
    public List<WritingSubmission> pendingWriting(UUID reviewer, boolean globalReviewer, int page) {
        return jdbc.query(
                """
                SELECT a.attempt_id,a.question_id,e.title AS exam_title,q.prompt,a.answer,
                       t.submitted_at
                FROM practice_answers a
                JOIN practice_attempts t ON t.id=a.attempt_id
                JOIN practice_questions q ON q.id=a.question_id
                JOIN practice_sections s ON s.id=q.section_id
                JOIN practice_exams e ON e.id=s.exam_id
                JOIN practice_exam_series es ON es.id=e.series_id
                LEFT JOIN practice_writing_reviews r
                  ON r.attempt_id=a.attempt_id AND r.question_id=a.question_id
                WHERE t.status='SUBMITTED' AND q.kind='WRITING' AND t.owner_id<>?
                  AND s.exam_id=t.exam_id
                  AND length(trim(a.answer))>0 AND r.attempt_id IS NULL
                  AND (? OR es.author_id IS NULL OR es.author_id=?)
                ORDER BY t.submitted_at,a.attempt_id,a.question_id
                LIMIT 30 OFFSET ?
                """,
                (row, index) ->
                        new WritingSubmission(
                                row.getObject("attempt_id", UUID.class),
                                row.getObject("question_id", UUID.class),
                                row.getString("exam_title"),
                                row.getString("prompt"),
                                row.getString("answer"),
                                row.getTimestamp("submitted_at").toInstant()),
                reviewer,
                globalReviewer,
                reviewer,
                (long) page * 30);
    }

    @Transactional
    public boolean reviewWriting(WritingReview review, boolean globalReviewer) {
        return jdbc.update(
                        """
                        INSERT INTO practice_writing_reviews(
                          attempt_id,question_id,reviewer_id,task_score,coherence_score,
                          vocabulary_score,grammar_score,feedback)
                        SELECT ?,?,?,?,?,?,?,?
                        FROM practice_attempts t
                        JOIN practice_answers a ON a.attempt_id=t.id
                        JOIN practice_questions q ON q.id=a.question_id
                        JOIN practice_sections s ON s.id=q.section_id AND s.exam_id=t.exam_id
                        JOIN practice_exams e ON e.id=t.exam_id
                        JOIN practice_exam_series es ON es.id=e.series_id
                        WHERE t.id=? AND a.question_id=? AND t.status='SUBMITTED'
                          AND q.kind='WRITING' AND length(trim(a.answer))>0
                          AND t.owner_id<>?
                          AND (? OR es.author_id IS NULL OR es.author_id=?)
                        ON CONFLICT DO NOTHING
                        """,
                        review.attemptId(),
                        review.questionId(),
                        review.reviewerId(),
                        review.taskScore(),
                        review.coherenceScore(),
                        review.vocabularyScore(),
                        review.grammarScore(),
                        review.feedback(),
                        review.attemptId(),
                        review.questionId(),
                        review.reviewerId(),
                        globalReviewer,
                        review.reviewerId())
                == 1;
    }

    @Transactional(readOnly = true)
    public List<ExamRevisionSummary> list(UUID actor, boolean admin, boolean publisher, int page) {
        return jdbc.query(
                """
                SELECT e.id,es.slug,e.title,e.duration_minutes,e.revision,e.status,e.version
                FROM practice_exams e JOIN practice_exam_series es ON es.id=e.series_id
                WHERE (? OR es.author_id=? OR (? AND e.status<>'DRAFT'))
                ORDER BY e.created_at DESC,e.id LIMIT 20 OFFSET ?
                """,
                (rs, row) ->
                        new ExamRevisionSummary(
                                rs.getObject("id", UUID.class),
                                rs.getString("slug"),
                                rs.getString("title"),
                                rs.getInt("duration_minutes"),
                                rs.getInt("revision"),
                                ExamRevisionStatus.valueOf(rs.getString("status")),
                                rs.getLong("version")),
                admin,
                actor,
                publisher,
                (long) page * 20);
    }

    @Transactional(readOnly = true)
    public Optional<ExamRevision> revision(UUID id) {
        return jdbc
                .query(
                        """
                        SELECT e.*,es.author_id FROM practice_exams e
                        JOIN practice_exam_series es ON es.id=e.series_id WHERE e.id=?
                        """,
                        (rs, row) ->
                                new ExamRevision(
                                        id,
                                        rs.getObject("series_id", UUID.class),
                                        rs.getObject("author_id", UUID.class),
                                        rs.getInt("revision"),
                                        ExamRevisionStatus.valueOf(rs.getString("status")),
                                        rs.getLong("version"),
                                        loadExam(id)),
                        id)
                .stream()
                .findFirst();
    }

    @Transactional
    public void create(ExamRevision draft) {
        try {
            jdbc.update(
                    "INSERT INTO practice_exam_series(id,slug,author_id) VALUES (?,?,?)",
                    draft.seriesId(),
                    draft.exam().slug(),
                    draft.authorId());
            insertDraft(draft);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "exam_slug_exists", ErrorType.CONFLICT, "Mã đề này đã được sử dụng.");
        }
    }

    @Transactional
    public boolean save(UUID id, long expectedVersion, PracticeExam exam) {
        int updated =
                jdbc.update(
                        """
UPDATE practice_exams SET title=?,description=?,duration_minutes=?,version=version+1
WHERE id=? AND version=? AND status='DRAFT'
""",
                        exam.title(),
                        exam.description(),
                        exam.durationMinutes(),
                        id,
                        expectedVersion);
        if (updated != 1) return false;
        jdbc.update("DELETE FROM practice_sections WHERE exam_id=?", id);
        insertContent(exam);
        return true;
    }

    @Transactional
    public boolean transition(
            UUID id, long expectedVersion, ExamRevisionStatus from, ExamRevisionStatus to) {
        return jdbc.update(
                        """
                        UPDATE practice_exams SET status=?,version=version+1
                        WHERE id=? AND version=? AND status=?
                        """,
                        to.name(),
                        id,
                        expectedVersion,
                        from.name())
                == 1;
    }

    @Transactional
    public boolean publish(UUID id, long expectedVersion, UUID publisher) {
        UUID seriesId = lockSeries(id);
        if (seriesId == null
                || jdbc.query(
                                """
SELECT id FROM practice_exams WHERE id=? AND version=? AND status='PENDING_REVIEW'
FOR UPDATE
""",
                                (rs, row) -> rs.getObject("id", UUID.class),
                                id,
                                expectedVersion)
                        .isEmpty()) {
            return false;
        }
        jdbc.update(
                """
                UPDATE practice_exams SET status='ARCHIVED',version=version+1
                WHERE series_id=? AND status='PUBLISHED'
                """,
                seriesId);
        return jdbc.update(
                        """
                        UPDATE practice_exams SET status='PUBLISHED',version=version+1,
                            published_at=CURRENT_TIMESTAMP,published_by=?
                        WHERE id=? AND version=? AND status='PENDING_REVIEW'
                        """,
                        publisher,
                        id,
                        expectedVersion)
                == 1;
    }

    @Transactional
    public boolean clonePublished(UUID sourceId, long expectedVersion, ExamRevision draft) {
        UUID seriesId = lockSeries(sourceId);
        if (seriesId == null
                || jdbc.query(
                                """
SELECT id FROM practice_exams WHERE id=? AND version=? AND status='PUBLISHED'
""",
                                (rs, row) -> rs.getObject("id", UUID.class),
                                sourceId,
                                expectedVersion)
                        .isEmpty()
                || !jdbc.query(
                                """
SELECT id FROM practice_exams WHERE series_id=? AND status IN ('DRAFT','PENDING_REVIEW')
""",
                                (rs, row) -> rs.getObject("id", UUID.class),
                                seriesId)
                        .isEmpty()) {
            return false;
        }
        insertDraft(draft);
        return true;
    }

    private UUID lockSeries(UUID revisionId) {
        return jdbc
                .query(
                        """
                        SELECT es.id FROM practice_exam_series es
                        JOIN practice_exams e ON e.series_id=es.id WHERE e.id=? FOR UPDATE OF es
                        """,
                        (rs, row) -> rs.getObject("id", UUID.class),
                        revisionId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void insertDraft(ExamRevision draft) {
        jdbc.update(
                """
INSERT INTO practice_exams(id,series_id,revision,title,description,duration_minutes)
VALUES (?,?,?,?,?,?)
""",
                draft.id(),
                draft.seriesId(),
                draft.revision(),
                draft.exam().title(),
                draft.exam().description(),
                draft.exam().durationMinutes());
        insertContent(draft.exam());
    }

    private void insertContent(PracticeExam exam) {
        for (int sectionOrder = 0; sectionOrder < exam.sections().size(); sectionOrder++) {
            PracticeExam.Section section = exam.sections().get(sectionOrder);
            jdbc.update(
                    """
INSERT INTO practice_sections(id,exam_id,skill,title,passage,audio_text,display_order)
VALUES (?,?,?,?,?,?,?)
""",
                    section.id(),
                    exam.id(),
                    section.skill(),
                    section.title(),
                    section.passage(),
                    section.audioText(),
                    sectionOrder);
            for (int questionOrder = 0;
                    questionOrder < section.questions().size();
                    questionOrder++) {
                PracticeExam.Question question = section.questions().get(questionOrder);
                int order = questionOrder;
                jdbc.update(
                        connection -> {
                            var statement =
                                    connection.prepareStatement(
                                            """
INSERT INTO practice_questions(id,section_id,kind,prompt,options,
    correct_answer,explanation,display_order) VALUES (?,?,?,?,?,?,?,?)
""");
                            statement.setObject(1, question.id());
                            statement.setObject(2, section.id());
                            statement.setString(3, question.kind());
                            statement.setString(4, question.prompt());
                            statement.setArray(
                                    5,
                                    connection.createArrayOf("text", question.options().toArray()));
                            statement.setString(6, question.correctAnswer());
                            statement.setString(7, question.explanation());
                            statement.setInt(8, order);
                            return statement;
                        });
            }
        }
    }
}
