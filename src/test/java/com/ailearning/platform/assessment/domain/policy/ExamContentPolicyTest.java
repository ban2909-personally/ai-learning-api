package com.ailearning.platform.assessment.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

class ExamContentPolicyTest {
    private PracticeExam exam(String skill, String kind, List<String> options, String key) {
        return new PracticeExam(
                UUID.randomUUID(),
                "sample",
                "Sample",
                "",
                10,
                List.of(
                        new PracticeExam.Section(
                                UUID.randomUUID(),
                                skill,
                                "Section",
                                "Passage",
                                "Audio",
                                List.of(
                                        new PracticeExam.Question(
                                                UUID.randomUUID(),
                                                kind,
                                                "Prompt",
                                                options,
                                                key,
                                                "Explanation")))));
    }

    @Test
    void draftMayBeIncompleteButCannotBeSubmittedEmpty() {
        PracticeExam empty =
                new PracticeExam(UUID.randomUUID(), "sample", "Sample", "", 10, List.of());
        assertThatCode(() -> ExamContentPolicy.validate(empty, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ExamContentPolicy.validate(empty, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validatesDistinctChoicesAndAnswerMembership() {
        assertThatCode(
                        () ->
                                ExamContentPolicy.validate(
                                        exam("READING", "CHOICE", List.of("A", "B"), "A"), true))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                ExamContentPolicy.validate(
                                        exam("READING", "CHOICE", List.of("A", "A"), "A"), true))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                ExamContentPolicy.validate(
                                        exam("READING", "CHOICE", List.of("A", "B"), "C"), true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsChoicesThatBecomeIdenticalUnderObjectiveGradingNormalization() {
        assertThatThrownBy(
                        () ->
                                ExamContentPolicy.validate(
                                        exam(
                                                "READING",
                                                "CHOICE",
                                                List.of(" Tuesday ", "tuesday"),
                                                "tuesday"),
                                        true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsWritingQuestionsInObjectiveSectionsAndViceVersa() {
        assertThatThrownBy(
                        () ->
                                ExamContentPolicy.validate(
                                        exam("READING", "WRITING", List.of(), null), false))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                ExamContentPolicy.validate(
                                        exam("WRITING", "TEXT", List.of(), "A"), false))
                .isInstanceOf(BusinessException.class);
        assertThatCode(
                        () ->
                                ExamContentPolicy.validate(
                                        exam("WRITING", "WRITING", List.of(), null), true))
                .doesNotThrowAnyException();
    }

    @Test
    void enforcesQuestionAndSectionBoundsEvenForDraft() {
        PracticeExam base = exam("READING", "TEXT", List.of(), "A");
        PracticeExam tooManySections =
                new PracticeExam(
                        base.id(),
                        base.slug(),
                        base.title(),
                        "",
                        10,
                        Collections.nCopies(21, base.sections().getFirst()));
        assertThatThrownBy(() -> ExamContentPolicy.validate(tooManySections, false))
                .isInstanceOf(BusinessException.class);
        PracticeExam.Section section = base.sections().getFirst();
        PracticeExam tooManyQuestions =
                new PracticeExam(
                        base.id(),
                        base.slug(),
                        base.title(),
                        "",
                        10,
                        List.of(
                                new PracticeExam.Section(
                                        section.id(),
                                        "READING",
                                        "Section",
                                        "",
                                        null,
                                        Collections.nCopies(251, section.questions().getFirst()))));
        assertThatThrownBy(() -> ExamContentPolicy.validate(tooManyQuestions, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void publicationRequiresObjectiveExplanationAndListeningSource() {
        PracticeExam.Question question =
                new PracticeExam.Question(
                        UUID.randomUUID(), "TEXT", "Prompt", List.of(), "answer", "");
        PracticeExam listening =
                new PracticeExam(
                        UUID.randomUUID(),
                        "sample",
                        "Sample",
                        "",
                        10,
                        List.of(
                                new PracticeExam.Section(
                                        UUID.randomUUID(),
                                        "LISTENING",
                                        "Listening",
                                        null,
                                        "",
                                        List.of(question))));
        assertThatThrownBy(() -> ExamContentPolicy.validate(listening, true))
                .isInstanceOf(BusinessException.class);
        PracticeExam reading =
                new PracticeExam(
                        listening.id(),
                        "sample",
                        "Sample",
                        "",
                        10,
                        List.of(
                                new PracticeExam.Section(
                                        UUID.randomUUID(),
                                        "READING",
                                        "Reading",
                                        "Text",
                                        null,
                                        List.of(question))));
        assertThatThrownBy(() -> ExamContentPolicy.validate(reading, true))
                .isInstanceOf(BusinessException.class);
    }
}
