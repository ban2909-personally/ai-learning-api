package com.ailearning.platform.assessment.application.port.out;

import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeStore {
    List<PracticeExam> publishedExams();

    Optional<PracticeExam> publishedExam(String slug);

    Optional<PracticeExam> publishedExam(UUID id);

    PracticeAttempt start(UUID examId, UUID ownerId);

    Optional<PracticeAttempt> attempt(UUID attemptId, UUID ownerId);

    boolean saveAnswer(UUID attemptId, UUID ownerId, UUID questionId, String answer);

    boolean submit(UUID attemptId, UUID ownerId);
}
