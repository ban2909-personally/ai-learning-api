package com.ailearning.platform.assessment.application.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.ailearning.platform.assessment.application.port.out.PracticeStore;
import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.identity.api.contract.UserView;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class PracticeServiceTest {
    private final PracticeStore store = mock(PracticeStore.class);
    private final AccountAccess access = mock(AccountAccess.class);
    private final PracticeService service = new PracticeService(store, access);

    @Test
    void rejectsChoiceOutsidePublishedOptionsWithoutWritingToPort() {
        UUID owner = UUID.randomUUID();
        when(access.requireActive(owner))
                .thenReturn(
                        new UserView(
                                owner, "student@example.invalid", "Student", Set.of("STUDENT")));
        UUID attemptId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        when(store.attempt(attemptId, owner))
                .thenReturn(
                        Optional.of(
                                new PracticeAttempt(
                                        attemptId, examId, owner, "IN_PROGRESS", Map.of())));
        when(store.attemptExam(examId))
                .thenReturn(
                        Optional.of(
                                new PracticeExam(
                                        examId,
                                        "sample",
                                        "Sample",
                                        "",
                                        10,
                                        List.of(
                                                new PracticeExam.Section(
                                                        UUID.randomUUID(),
                                                        "READING",
                                                        "Reading",
                                                        "",
                                                        null,
                                                        List.of(
                                                                new PracticeExam.Question(
                                                                        questionId,
                                                                        "CHOICE",
                                                                        "Prompt",
                                                                        List.of("A", "B"),
                                                                        "A",
                                                                        "")))))));

        assertThatThrownBy(() -> service.answer(owner, attemptId, questionId, "injected option"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Đáp án không hợp lệ.");
        verify(store, never()).saveAnswer(any(), any(), any(), any());
    }

    @Test
    void refusesResultBeforeSubmission() {
        UUID owner = UUID.randomUUID();
        when(access.requireActive(owner))
                .thenReturn(
                        new UserView(
                                owner, "student@example.invalid", "Student", Set.of("STUDENT")));
        UUID attemptId = UUID.randomUUID();
        when(store.attempt(attemptId, owner))
                .thenReturn(
                        Optional.of(
                                new PracticeAttempt(
                                        attemptId,
                                        UUID.randomUUID(),
                                        owner,
                                        "IN_PROGRESS",
                                        Map.of())));

        assertThatThrownBy(() -> service.result(owner, attemptId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Hãy nộp bài trước khi xem kết quả.");
    }

    @Test
    void lecturerCannotReviewOwnWriting() {
        UUID lecturer = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        when(access.requireActive(lecturer))
                .thenReturn(
                        new UserView(
                                lecturer, "teacher@example.invalid", "Teacher", Set.of("LECTURE")));
        when(store.attempt(attemptId, lecturer))
                .thenReturn(
                        Optional.of(
                                new PracticeAttempt(
                                        attemptId,
                                        UUID.randomUUID(),
                                        lecturer,
                                        "SUBMITTED",
                                        Map.of(questionId, "My answer"))));

        assertThatThrownBy(
                        () ->
                                service.reviewWriting(
                                        lecturer, attemptId, questionId, 4, 4, 4, 4, "Good work"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể tự chấm bài của mình.");
        verify(store, never()).reviewWriting(any(), anyBoolean());
    }

    @Test
    void guestCannotStartOrReadAttemptsThroughUseCase() {
        UUID guest = UUID.randomUUID();
        when(access.requireActive(guest))
                .thenReturn(new UserView(guest, "guest@example.invalid", "Guest", Set.of("GUEST")));

        assertThatThrownBy(() -> service.start(guest, "sample"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tài khoản không có quyền làm bài luyện tập.");
        assertThatThrownBy(() -> service.attempt(guest, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tài khoản không có quyền làm bài luyện tập.");
        verifyNoInteractions(store);
    }
}
