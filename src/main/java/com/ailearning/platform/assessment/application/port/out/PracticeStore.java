package com.ailearning.platform.assessment.application.port.out;

import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.assessment.domain.model.PracticeExamSummary;
import com.ailearning.platform.assessment.domain.model.WritingReview;
import com.ailearning.platform.assessment.domain.model.WritingSubmission;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PracticeStore {
    List<PracticeExamSummary> publishedExams();

    Optional<PracticeExam> publishedExam(String slug);

    Optional<PracticeExam> attemptExam(UUID id);

    PracticeAttempt start(UUID examId, UUID ownerId);

    Optional<PracticeAttempt> attempt(UUID attemptId, UUID ownerId);

    boolean saveAnswer(UUID attemptId, UUID ownerId, UUID questionId, String answer);

    boolean submit(UUID attemptId, UUID ownerId);

    Map<UUID, WritingReview> writingReviews(UUID attemptId);

    List<WritingSubmission> pendingWriting(UUID reviewer, boolean globalReviewer, int page);

    boolean reviewWriting(WritingReview review, boolean globalReviewer);
}
