package com.ailearning.platform.assessment.application.service.impl;

import com.ailearning.platform.assessment.api.contract.ExamDraftContent;
import com.ailearning.platform.assessment.api.usecase.ExamAuthoringUseCase;
import com.ailearning.platform.assessment.application.port.out.ExamAuthoringStore;
import com.ailearning.platform.assessment.domain.enumtype.ExamRevisionStatus;
import com.ailearning.platform.assessment.domain.enumtype.QuestionKind;
import com.ailearning.platform.assessment.domain.model.ExamRevision;
import com.ailearning.platform.assessment.domain.model.ExamRevisionSummary;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.assessment.domain.policy.ExamAuthoringPolicy;
import com.ailearning.platform.assessment.domain.policy.ExamContentPolicy;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ExamAuthoringService implements ExamAuthoringUseCase {
    private final ExamAuthoringStore store;
    private final AccountAccess access;

    public ExamAuthoringService(ExamAuthoringStore store, AccountAccess access) {
        this.store = store;
        this.access = access;
    }

    public List<ExamRevisionSummary> list(UUID actor, int page) {
        Set<String> roles = roles(actor);
        ExamAuthoringPolicy.requireStaff(roles);
        if (page < 0 || page > 10000) {
            throw new BusinessException(
                    "invalid_exam_page", ErrorType.BAD_REQUEST, "Trang không hợp lệ.");
        }
        return store.list(
                actor, roles.contains("ADMIN"), ExamAuthoringPolicy.isPublisher(roles), page);
    }

    public ExamRevision workspace(UUID actor, UUID id) {
        Set<String> roles = roles(actor);
        ExamAuthoringPolicy.requireStaff(roles);
        ExamRevision revision = load(id);
        ExamAuthoringPolicy.requireRead(actor, roles, revision);
        return revision;
    }

    public ExamRevision create(
            UUID actor, String slug, String title, String description, int minutes) {
        ExamAuthoringPolicy.requireAuthor(roles(actor));
        if (slug == null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 120) {
            throw new BusinessException(
                    "invalid_exam_slug", ErrorType.BAD_REQUEST, "Mã đề không hợp lệ.");
        }
        UUID id = UUID.randomUUID();
        ExamRevision draft =
                new ExamRevision(
                        id,
                        UUID.randomUUID(),
                        actor,
                        1,
                        ExamRevisionStatus.DRAFT,
                        0,
                        new PracticeExam(id, slug, title, description, minutes, List.of()));
        ExamContentPolicy.validate(draft.exam(), false);
        store.create(draft);
        return draft;
    }

    public ExamRevision save(UUID actor, UUID id, long version, ExamDraftContent content) {
        ExamRevision revision = editable(actor, id, version, ExamRevisionStatus.DRAFT);
        PracticeExam exam = content(revision, id, content);
        ExamContentPolicy.validate(exam, false);
        if (!store.save(id, version, exam)) throw ExamAuthoringPolicy.conflict();
        return load(id);
    }

    public ExamRevision submit(UUID actor, UUID id, long version) {
        ExamRevision revision = editable(actor, id, version, ExamRevisionStatus.DRAFT);
        ExamContentPolicy.validate(revision.exam(), true);
        return transition(id, version, ExamRevisionStatus.DRAFT, ExamRevisionStatus.PENDING_REVIEW);
    }

    public ExamRevision withdraw(UUID actor, UUID id, long version) {
        editable(actor, id, version, ExamRevisionStatus.PENDING_REVIEW);
        return transition(id, version, ExamRevisionStatus.PENDING_REVIEW, ExamRevisionStatus.DRAFT);
    }

    public ExamRevision publish(UUID actor, UUID id, long version) {
        ExamAuthoringPolicy.requirePublisher(roles(actor));
        ExamRevision revision = load(id);
        ExamAuthoringPolicy.requireState(revision, version, ExamRevisionStatus.PENDING_REVIEW);
        ExamContentPolicy.validate(revision.exam(), true);
        if (!store.publish(id, version, actor)) throw ExamAuthoringPolicy.conflict();
        return load(id);
    }

    public ExamRevision clonePublished(UUID actor, UUID id, long version) {
        ExamRevision revision = editable(actor, id, version, ExamRevisionStatus.PUBLISHED);
        UUID draftId = UUID.randomUUID();
        PracticeExam source = revision.exam();
        PracticeExam copy =
                new PracticeExam(
                        draftId,
                        source.slug(),
                        source.title(),
                        source.description(),
                        source.durationMinutes(),
                        source.sections().stream().map(this::copySection).toList());
        ExamRevision draft =
                new ExamRevision(
                        draftId,
                        revision.seriesId(),
                        revision.authorId(),
                        revision.revision() + 1,
                        ExamRevisionStatus.DRAFT,
                        0,
                        copy);
        if (!store.clonePublished(id, version, draft)) throw ExamAuthoringPolicy.conflict();
        return draft;
    }

    private ExamRevision editable(UUID actor, UUID id, long version, ExamRevisionStatus state) {
        Set<String> roles = roles(actor);
        ExamAuthoringPolicy.requireAuthor(roles);
        ExamRevision revision = load(id);
        ExamAuthoringPolicy.requireEdit(actor, roles, revision);
        ExamAuthoringPolicy.requireState(revision, version, state);
        return revision;
    }

    private ExamRevision transition(
            UUID id, long version, ExamRevisionStatus from, ExamRevisionStatus to) {
        if (!store.transition(id, version, from, to)) throw ExamAuthoringPolicy.conflict();
        return load(id);
    }

    private PracticeExam content(ExamRevision revision, UUID id, ExamDraftContent content) {
        return new PracticeExam(
                id,
                revision.exam().slug(),
                content.title(),
                content.description(),
                content.durationMinutes(),
                content.sections().stream().map(this::section).toList());
    }

    private PracticeExam.Section copySection(PracticeExam.Section source) {
        return new PracticeExam.Section(
                UUID.randomUUID(),
                source.skill(),
                source.title(),
                source.passage(),
                source.audioText(),
                source.questions().stream().map(this::copyQuestion).toList());
    }

    private PracticeExam.Question copyQuestion(PracticeExam.Question source) {
        return new PracticeExam.Question(
                UUID.randomUUID(),
                source.kind(),
                source.prompt(),
                source.options(),
                source.correctAnswer(),
                source.explanation());
    }

    private PracticeExam.Section section(ExamDraftContent.SectionInput input) {
        return new PracticeExam.Section(
                UUID.randomUUID(),
                input.skill().name(),
                input.title(),
                input.passage(),
                input.audioText(),
                input.questions().stream().map(this::question).toList());
    }

    private PracticeExam.Question question(ExamDraftContent.QuestionInput input) {
        String key = input.correctAnswer();
        if (input.kind() != QuestionKind.WRITING && key == null) key = "";
        return new PracticeExam.Question(
                UUID.randomUUID(),
                input.kind().name(),
                input.prompt(),
                input.options(),
                key,
                input.explanation());
    }

    private Set<String> roles(UUID actor) {
        return access.requireActive(actor).roles();
    }

    private ExamRevision load(UUID id) {
        return store.revision(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "exam_revision_not_found",
                                        ErrorType.NOT_FOUND,
                                        "Không tìm thấy phiên bản đề."));
    }
}
