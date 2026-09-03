package com.ailearning.platform.mentoring.api.usecase;

import com.ailearning.platform.mentoring.api.contract.MentorAnswerObserver;
import com.ailearning.platform.mentoring.api.contract.MentorMessageView;
import com.ailearning.platform.mentoring.api.contract.MentorTurnView;
import com.ailearning.platform.mentoring.domain.valueobject.MentorQuestion;

import java.util.List;
import java.util.UUID;

public interface MentorUseCase {
    List<MentorMessageView> history(UUID userId, String courseSlug, UUID lessonId);

    MentorTurnView ask(
            UUID userId,
            String courseSlug,
            UUID lessonId,
            MentorQuestion question,
            MentorAnswerObserver observer
    );
}

