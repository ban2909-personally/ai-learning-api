package com.ailearning.platform.assessment.application.service.impl;

import com.ailearning.platform.assessment.api.usecase.PracticeUseCase;
import com.ailearning.platform.assessment.application.port.out.PracticeStore;
import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.assessment.domain.model.WritingReview;
import com.ailearning.platform.assessment.domain.model.WritingSubmission;
import com.ailearning.platform.assessment.domain.service.PracticeGrader;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PracticeService implements PracticeUseCase {
    private static final Set<String> REVIEWER_ROLES = Set.of("LECTURE", "INSTRUCTOR", "LEADER", "ADMIN");
    private final PracticeStore store;
    private final AccountAccess access;

    public PracticeService(PracticeStore store, AccountAccess access) {
        this.store = store;
        this.access = access;
    }

    public List<PracticeExam> list() {
        return store.publishedExams();
    }

    public PracticeExam exam(String slug) {
        return store.publishedExam(slug).orElseThrow(() -> notFound("Không tìm thấy đề luyện tập."));
    }

    public PracticeExam exam(UUID id) {
        return store.publishedExam(id).orElseThrow(() -> notFound("Không tìm thấy đề luyện tập."));
    }

    public PracticeAttempt start(UUID actor, String slug) {
        access.requireActive(actor);
        return store.start(exam(slug).id(), actor);
    }

    public PracticeAttempt attempt(UUID actor, UUID id) {
        access.requireActive(actor);
        return store.attempt(id, actor).orElseThrow(() -> notFound("Không tìm thấy lượt làm bài."));
    }

    public PracticeAttempt answer(UUID actor, UUID id, UUID questionId, String answer) {
        PracticeAttempt attempt = attempt(actor, id);
        if (!"IN_PROGRESS".equals(attempt.status())) {
            throw new BusinessException("attempt_submitted", ErrorType.CONFLICT,
                    "Bài đã nộp, không thể sửa đáp án.");
        }
        PracticeExam exam = exam(attempt.examId());
        PracticeExam.Question question = exam.sections().stream()
                .flatMap(section -> section.questions().stream())
                .filter(candidate -> candidate.id().equals(questionId)).findFirst()
                .orElseThrow(() -> notFound("Câu hỏi không thuộc đề này."));
        String response = answer == null ? "" : answer.trim();
        if (response.length() > ("WRITING".equals(question.kind()) ? 10000 : 500)
                || ("CHOICE".equals(question.kind()) && !response.isEmpty()
                && !question.options().contains(response))) {
            throw new BusinessException("invalid_practice_answer", ErrorType.BAD_REQUEST,
                    "Đáp án không hợp lệ.");
        }
        if (!store.saveAnswer(id, actor, questionId, response)) {
            throw new BusinessException("attempt_submitted", ErrorType.CONFLICT,
                    "Bài đã nộp, không thể sửa đáp án.");
        }
        return attempt(actor, id);
    }

    public PracticeGrader.Result submit(UUID actor, UUID id) {
        PracticeAttempt attempt = attempt(actor, id);
        if (!"IN_PROGRESS".equals(attempt.status()) || !store.submit(id, actor)) {
            throw new BusinessException("attempt_submitted", ErrorType.CONFLICT,
                    "Bài đã được nộp trước đó.");
        }
        return result(actor, id);
    }

    public PracticeGrader.Result result(UUID actor, UUID id) {
        PracticeAttempt attempt = attempt(actor, id);
        if (!"SUBMITTED".equals(attempt.status())) {
            throw new BusinessException("attempt_in_progress", ErrorType.CONFLICT,
                    "Hãy nộp bài trước khi xem kết quả.");
        }
        PracticeExam exam = exam(attempt.examId());
        return PracticeGrader.grade(exam, attempt, store.writingReviews(id));
    }

    public List<WritingSubmission> pendingWriting(UUID reviewer, int page) {
        requireReviewer(reviewer);
        if (page < 0 || page > 10000) {
            throw new BusinessException("invalid_review_page", ErrorType.BAD_REQUEST,
                    "Trang danh sách không hợp lệ.");
        }
        return store.pendingWriting(reviewer, page);
    }

    public WritingReview reviewWriting(UUID reviewer, UUID attemptId, UUID questionId,
                                       int taskScore, int coherenceScore, int vocabularyScore,
                                       int grammarScore, String feedback) {
        requireReviewer(reviewer);
        if (store.attempt(attemptId, reviewer).isPresent()) {
            throw new BusinessException("self_review_forbidden", ErrorType.FORBIDDEN,
                    "Không thể tự chấm bài của mình.");
        }
        String comment = feedback == null ? "" : feedback.trim();
        if (comment.isEmpty() || comment.length() > 2000
                || !validScore(taskScore) || !validScore(coherenceScore)
                || !validScore(vocabularyScore) || !validScore(grammarScore)) {
            throw new BusinessException("invalid_writing_review", ErrorType.BAD_REQUEST,
                    "Cần nhận xét và điểm từ 0 đến 5 cho mỗi tiêu chí.");
        }
        WritingReview review = new WritingReview(attemptId, questionId, reviewer,
                taskScore, coherenceScore, vocabularyScore, grammarScore, comment);
        if (!store.reviewWriting(review)) {
            throw new BusinessException("writing_review_unavailable", ErrorType.CONFLICT,
                    "Bài viết không chờ chấm hoặc đã có người chấm.");
        }
        return review;
    }

    private void requireReviewer(UUID actor) {
        if (access.requireActive(actor).roles().stream().noneMatch(REVIEWER_ROLES::contains)) {
            throw new BusinessException("writing_review_forbidden", ErrorType.FORBIDDEN,
                    "Tài khoản không có quyền chấm bài viết.");
        }
    }

    private static boolean validScore(int score) {
        return score >= 0 && score <= 5;
    }

    private static BusinessException notFound(String message) {
        return new BusinessException("practice_not_found", ErrorType.NOT_FOUND, message);
    }
}
