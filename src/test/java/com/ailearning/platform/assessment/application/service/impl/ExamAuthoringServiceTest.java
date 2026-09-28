package com.ailearning.platform.assessment.application.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailearning.platform.assessment.application.port.out.ExamAuthoringStore;
import com.ailearning.platform.assessment.domain.enumtype.ExamRevisionStatus;
import com.ailearning.platform.assessment.domain.model.ExamRevision;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.identity.api.contract.UserView;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class ExamAuthoringServiceTest {
    private final ExamAuthoringStore store = mock(ExamAuthoringStore.class);
    private final AccountAccess access = mock(AccountAccess.class);
    private final ExamAuthoringService service = new ExamAuthoringService(store, access);
    private final UUID author = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();

    private void role(UUID actor, String role) {
        when(access.requireActive(actor))
                .thenReturn(new UserView(actor, "x@example.invalid", "Staff", Set.of(role)));
    }

    private ExamRevision revision(ExamRevisionStatus status) {
        PracticeExam exam =
                new PracticeExam(
                        id,
                        "sample",
                        "Sample",
                        "",
                        10,
                        List.of(
                                new PracticeExam.Section(
                                        UUID.randomUUID(),
                                        "READING",
                                        "Reading",
                                        "Passage",
                                        null,
                                        List.of(
                                                new PracticeExam.Question(
                                                        UUID.randomUUID(),
                                                        "TEXT",
                                                        "Prompt",
                                                        List.of(),
                                                        "A",
                                                        "Explanation")))));
        return new ExamRevision(id, UUID.randomUUID(), author, 1, status, 2, exam);
    }

    @Test
    void studentCannotReadOrCreatePrivateExams() {
        role(author, "STUDENT");
        assertThatThrownBy(() -> service.workspace(author, id))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(author, "sample", "Title", "", 10))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(store);
    }

    @Test
    void lecturerCannotAccessAnotherAuthorsKeysOrWrite() {
        UUID other = UUID.randomUUID();
        role(other, "LECTURE");
        when(store.revision(id)).thenReturn(Optional.of(revision(ExamRevisionStatus.DRAFT)));
        assertThatThrownBy(() -> service.workspace(other, id))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.submit(other, id, 2))
                .isInstanceOf(BusinessException.class);
        verify(store, never()).transition(any(), anyLong(), any(), any());
    }

    @Test
    void leaderCanReadSubmittedContentButNotPrivateDrafts() {
        UUID leader = UUID.randomUUID();
        role(leader, "LEADER");
        ExamRevision draft = revision(ExamRevisionStatus.DRAFT);
        when(store.revision(id)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.workspace(leader, id))
                .isInstanceOf(BusinessException.class);
        when(store.revision(id))
                .thenReturn(Optional.of(revision(ExamRevisionStatus.PENDING_REVIEW)));
        assertThat(service.workspace(leader, id).status())
                .isEqualTo(ExamRevisionStatus.PENDING_REVIEW);
    }

    @Test
    void staleVersionNeverWritesAndPublicationCannotBeEditedInPlace() {
        role(author, "LECTURE");
        when(store.revision(id)).thenReturn(Optional.of(revision(ExamRevisionStatus.DRAFT)));
        assertThatThrownBy(() -> service.submit(author, id, 1))
                .isInstanceOf(BusinessException.class);
        when(store.revision(id)).thenReturn(Optional.of(revision(ExamRevisionStatus.PUBLISHED)));
        assertThatThrownBy(() -> service.submit(author, id, 2))
                .isInstanceOf(BusinessException.class);
        verify(store, never()).transition(any(), anyLong(), any(), any());
    }

    @Test
    void onlyPublisherCanPublishAndCasFailureIsExplicitConflict() {
        role(author, "LECTURE");
        assertThatThrownBy(() -> service.publish(author, id, 2))
                .isInstanceOf(BusinessException.class);
        role(author, "LEADER");
        when(store.revision(id))
                .thenReturn(Optional.of(revision(ExamRevisionStatus.PENDING_REVIEW)));
        when(store.publish(id, 2, author)).thenReturn(false);
        assertThatThrownBy(() -> service.publish(author, id, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Hãy tải lại");
    }

    @Test
    void cloningPreservesContentButGeneratesNewRevisionSectionAndQuestionIds() {
        role(author, "LECTURE");
        ExamRevision source = revision(ExamRevisionStatus.PUBLISHED);
        when(store.revision(id)).thenReturn(Optional.of(source));
        when(store.clonePublished(eq(id), eq(2L), any())).thenReturn(true);
        ExamRevision copy = service.clonePublished(author, id, 2);
        assertThat(copy.revision()).isEqualTo(2);
        assertThat(copy.seriesId()).isEqualTo(source.seriesId());
        assertThat(copy.id()).isNotEqualTo(id);
        assertThat(copy.exam().sections().getFirst().id())
                .isNotEqualTo(source.exam().sections().getFirst().id());
        assertThat(copy.exam().sections().getFirst().questions().getFirst().id())
                .isNotEqualTo(source.exam().sections().getFirst().questions().getFirst().id());
        assertThat(copy.exam().sections().getFirst().questions().getFirst().correctAnswer())
                .isEqualTo("A");
    }

    @Test
    void creationValidatesSlugAndStoresOwnedDraft() {
        role(author, "LECTURE");
        assertThatThrownBy(() -> service.create(author, "Bad Slug", "Title", "", 10))
                .isInstanceOf(BusinessException.class);
        service.create(author, "english-email", "English email", "", 15);
        ArgumentCaptor<ExamRevision> captor = ArgumentCaptor.forClass(ExamRevision.class);
        verify(store).create(captor.capture());
        assertThat(captor.getValue().authorId()).isEqualTo(author);
        assertThat(captor.getValue().status()).isEqualTo(ExamRevisionStatus.DRAFT);
    }
}
