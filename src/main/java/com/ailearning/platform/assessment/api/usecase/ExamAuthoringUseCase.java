package com.ailearning.platform.assessment.api.usecase;

import com.ailearning.platform.assessment.api.contract.ExamDraftContent;
import com.ailearning.platform.assessment.domain.model.ExamRevision;
import com.ailearning.platform.assessment.domain.model.ExamRevisionSummary;

import java.util.List;
import java.util.UUID;

public interface ExamAuthoringUseCase {
    List<ExamRevisionSummary> list(UUID actor, int page);

    ExamRevision workspace(UUID actor, UUID id);

    ExamRevision create(
            UUID actor, String slug, String title, String description, int durationMinutes);

    ExamRevision save(UUID actor, UUID id, long expectedVersion, ExamDraftContent content);

    ExamRevision submit(UUID actor, UUID id, long expectedVersion);

    ExamRevision withdraw(UUID actor, UUID id, long expectedVersion);

    ExamRevision publish(UUID actor, UUID id, long expectedVersion);

    ExamRevision clonePublished(UUID actor, UUID id, long expectedVersion);
}
