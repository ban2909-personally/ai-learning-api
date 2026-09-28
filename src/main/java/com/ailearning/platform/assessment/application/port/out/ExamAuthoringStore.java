package com.ailearning.platform.assessment.application.port.out;

import com.ailearning.platform.assessment.domain.enumtype.ExamRevisionStatus;
import com.ailearning.platform.assessment.domain.model.ExamRevision;
import com.ailearning.platform.assessment.domain.model.ExamRevisionSummary;
import com.ailearning.platform.assessment.domain.model.PracticeExam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamAuthoringStore {
    List<ExamRevisionSummary> list(UUID actor, boolean admin, boolean publisher, int page);

    Optional<ExamRevision> revision(UUID id);

    void create(ExamRevision draft);

    boolean save(UUID id, long expectedVersion, PracticeExam exam);

    boolean transition(
            UUID id, long expectedVersion, ExamRevisionStatus from, ExamRevisionStatus to);

    boolean publish(UUID id, long expectedVersion, UUID publisher);

    boolean clonePublished(UUID sourceId, long expectedVersion, ExamRevision draft);
}
