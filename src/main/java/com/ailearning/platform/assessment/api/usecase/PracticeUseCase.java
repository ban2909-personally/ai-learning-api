package com.ailearning.platform.assessment.api.usecase;

import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.assessment.domain.model.WritingReview;
import com.ailearning.platform.assessment.domain.model.WritingSubmission;
import com.ailearning.platform.assessment.domain.service.PracticeGrader;

import java.util.List;
import java.util.UUID;

public interface PracticeUseCase {
    List<PracticeExam> list();

    PracticeExam exam(String slug);

    PracticeExam exam(UUID id);

    PracticeAttempt start(UUID actor, String slug);

    PracticeAttempt attempt(UUID actor, UUID id);

    PracticeAttempt answer(UUID actor, UUID id, UUID questionId, String answer);

    PracticeGrader.Result submit(UUID actor, UUID id);

    PracticeGrader.Result result(UUID actor, UUID id);

    List<WritingSubmission> pendingWriting(UUID reviewer, int page);

    WritingReview reviewWriting(UUID reviewer, UUID attemptId, UUID questionId,
                                int taskScore, int coherenceScore, int vocabularyScore,
                                int grammarScore, String feedback);
}
