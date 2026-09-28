package com.ailearning.platform.assessment.adapter.out.persistence;

import com.ailearning.platform.assessment.application.port.out.PracticeStore;
import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.assessment.domain.model.WritingReview;
import com.ailearning.platform.assessment.domain.model.WritingSubmission;
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
public class PracticePersistenceAdapter implements PracticeStore {
    private final JdbcTemplate jdbc;

    public PracticePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PracticeExam> publishedExams() {
        return jdbc.query("SELECT id FROM practice_exams WHERE published ORDER BY created_at DESC, id",
                (rs, row) -> rs.getObject("id", UUID.class)).stream()
                .map(this::loadExam).toList();
    }

    @Transactional(readOnly = true)
    public Optional<PracticeExam> publishedExam(String slug) {
        return jdbc.query("SELECT id FROM practice_exams WHERE slug=? AND published",
                (rs, row) -> rs.getObject("id", UUID.class), slug).stream()
                .findFirst().map(this::loadExam);
    }

    @Transactional(readOnly = true)
    public Optional<PracticeExam> publishedExam(UUID id) {
        return jdbc.query("SELECT id FROM practice_exams WHERE id=? AND published",
                (rs, row) -> rs.getObject("id", UUID.class), id).stream()
                .findFirst().map(this::loadExam);
    }

    private PracticeExam loadExam(UUID id) {
        return jdbc.query("SELECT * FROM practice_exams WHERE id=?",
                (rs, row) -> new PracticeExam(id, rs.getString("slug"),
                        rs.getString("title"), rs.getString("description"),
                        rs.getInt("duration_minutes"), sections(id)), id).getFirst();
    }

    private List<PracticeExam.Section> sections(UUID examId) {
        return jdbc.query("SELECT * FROM practice_sections WHERE exam_id=? ORDER BY display_order",
                (rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new PracticeExam.Section(id, rs.getString("skill"),
                            rs.getString("title"), rs.getString("passage"),
                            rs.getString("audio_text"), questions(id));
                }, examId);
    }

    private List<PracticeExam.Question> questions(UUID sectionId) {
        return jdbc.query("SELECT * FROM practice_questions WHERE section_id=? ORDER BY display_order",
                (rs, row) -> new PracticeExam.Question(
                        rs.getObject("id", UUID.class), rs.getString("kind"),
                        rs.getString("prompt"), strings(rs.getArray("options")),
                        rs.getString("correct_answer"), rs.getString("explanation")), sectionId);
    }

    private static List<String> strings(Array array) throws SQLException {
        return Arrays.asList((String[]) array.getArray());
    }

    @Transactional
    public PracticeAttempt start(UUID examId, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO practice_attempts(id,exam_id,owner_id) VALUES (?,?,?)",
                id, examId, ownerId);
        return new PracticeAttempt(id, examId, ownerId, "IN_PROGRESS", Map.of());
    }

    @Transactional(readOnly = true)
    public Optional<PracticeAttempt> attempt(UUID attemptId, UUID ownerId) {
        return jdbc.query("SELECT * FROM practice_attempts WHERE id=? AND owner_id=?",
                (rs, row) -> {
                    Map<UUID, String> answers = new HashMap<>();
                    jdbc.query("SELECT question_id, answer FROM practice_answers WHERE attempt_id=?",
                            (org.springframework.jdbc.core.RowCallbackHandler) response -> {
                                answers.put(response.getObject("question_id", UUID.class),
                                        response.getString("answer"));
                            }, attemptId);
                    return new PracticeAttempt(attemptId, rs.getObject("exam_id", UUID.class),
                            ownerId, rs.getString("status"), answers);
                }, attemptId, ownerId).stream().findFirst();
    }

    @Transactional
    public boolean saveAnswer(UUID attemptId, UUID ownerId, UUID questionId, String answer) {
        boolean editable = !jdbc.query(
                "SELECT id FROM practice_attempts WHERE id=? AND owner_id=? AND status='IN_PROGRESS' FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), attemptId, ownerId).isEmpty();
        if (!editable) return false;
        jdbc.update("""
                INSERT INTO practice_answers(attempt_id,question_id,answer) VALUES (?,?,?)
                ON CONFLICT(attempt_id,question_id) DO UPDATE SET answer=EXCLUDED.answer
                """, attemptId, questionId, answer);
        return true;
    }

    @Transactional
    public boolean submit(UUID attemptId, UUID ownerId) {
        return jdbc.update("""
                UPDATE practice_attempts SET status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP
                WHERE id=? AND owner_id=? AND status='IN_PROGRESS'
                """, attemptId, ownerId) == 1;
    }

    @Transactional(readOnly = true)
    public Map<UUID, WritingReview> writingReviews(UUID attemptId) {
        Map<UUID, WritingReview> reviews = new HashMap<>();
        jdbc.query("SELECT * FROM practice_writing_reviews WHERE attempt_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                    UUID questionId = row.getObject("question_id", UUID.class);
                    reviews.put(questionId, new WritingReview(attemptId, questionId,
                            row.getObject("reviewer_id", UUID.class),
                            row.getInt("task_score"), row.getInt("coherence_score"),
                            row.getInt("vocabulary_score"), row.getInt("grammar_score"),
                            row.getString("feedback")));
                }, attemptId);
        return Map.copyOf(reviews);
    }

    @Transactional(readOnly = true)
    public List<WritingSubmission> pendingWriting(UUID reviewer, int page) {
        return jdbc.query("""
                SELECT a.attempt_id,a.question_id,e.title AS exam_title,q.prompt,a.answer,
                       t.submitted_at
                FROM practice_answers a
                JOIN practice_attempts t ON t.id=a.attempt_id
                JOIN practice_questions q ON q.id=a.question_id
                JOIN practice_sections s ON s.id=q.section_id
                JOIN practice_exams e ON e.id=s.exam_id
                LEFT JOIN practice_writing_reviews r
                  ON r.attempt_id=a.attempt_id AND r.question_id=a.question_id
                WHERE t.status='SUBMITTED' AND q.kind='WRITING' AND t.owner_id<>?
                  AND s.exam_id=t.exam_id
                  AND length(trim(a.answer))>0 AND r.attempt_id IS NULL
                ORDER BY t.submitted_at,a.attempt_id,a.question_id
                LIMIT 30 OFFSET ?
                """, (row, index) -> new WritingSubmission(
                row.getObject("attempt_id", UUID.class),
                row.getObject("question_id", UUID.class), row.getString("exam_title"),
                row.getString("prompt"), row.getString("answer"),
                row.getTimestamp("submitted_at").toInstant()), reviewer, (long) page * 30);
    }

    @Transactional
    public boolean reviewWriting(WritingReview review) {
        return jdbc.update("""
                INSERT INTO practice_writing_reviews(
                  attempt_id,question_id,reviewer_id,task_score,coherence_score,
                  vocabulary_score,grammar_score,feedback)
                SELECT ?,?,?,?,?,?,?,?
                FROM practice_attempts t
                JOIN practice_answers a ON a.attempt_id=t.id
                JOIN practice_questions q ON q.id=a.question_id
                JOIN practice_sections s ON s.id=q.section_id AND s.exam_id=t.exam_id
                WHERE t.id=? AND a.question_id=? AND t.status='SUBMITTED'
                  AND q.kind='WRITING' AND length(trim(a.answer))>0
                  AND t.owner_id<>?
                ON CONFLICT DO NOTHING
                """, review.attemptId(), review.questionId(), review.reviewerId(),
                review.taskScore(), review.coherenceScore(), review.vocabularyScore(),
                review.grammarScore(), review.feedback(), review.attemptId(),
                review.questionId(), review.reviewerId()) == 1;
    }
}
