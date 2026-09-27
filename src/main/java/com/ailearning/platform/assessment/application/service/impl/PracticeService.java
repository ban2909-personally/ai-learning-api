package com.ailearning.platform.assessment.application.service.impl;

import com.ailearning.platform.assessment.api.usecase.PracticeUseCase;
import com.ailearning.platform.assessment.application.port.out.PracticeStore;
import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.assessment.domain.service.PracticeGrader;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.List;
import java.util.UUID;

public class PracticeService implements PracticeUseCase {
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
        return PracticeGrader.grade(exam, attempt);
    }

    private static BusinessException notFound(String message) {
        return new BusinessException("practice_not_found", ErrorType.NOT_FOUND, message);
    }
}
