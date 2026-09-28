package com.ailearning.platform.assessment.domain.service;

import com.ailearning.platform.assessment.domain.model.PracticeAttempt;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.assessment.domain.model.WritingReview;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeGraderTest {
    @Test
    void gradesObjectiveAnswersButNeverAssignsWritingScore() {
        UUID choiceId = UUID.randomUUID();
        UUID writingId = UUID.randomUUID();
        PracticeExam exam = new PracticeExam(UUID.randomUUID(), "sample", "Sample", "", 20,
                List.of(
                        new PracticeExam.Section(UUID.randomUUID(), "READING", "Reading", "", null,
                                List.of(new PracticeExam.Question(choiceId, "CHOICE", "Question",
                                        List.of("Yes", "No"), "Yes", "Because."))),
                        new PracticeExam.Section(UUID.randomUUID(), "WRITING", "Writing", "", null,
                                List.of(new PracticeExam.Question(writingId, "WRITING", "Prompt",
                                        List.of(), null, "")))));
        PracticeAttempt attempt = new PracticeAttempt(UUID.randomUUID(), exam.id(), UUID.randomUUID(),
                "SUBMITTED", Map.of(choiceId, " yes ", writingId, "An original response."));

        var result = PracticeGrader.grade(exam, attempt, Map.of());

        assertThat(result.correct()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.sections().get(1).questions().getFirst().status())
                .isEqualTo("PENDING_REVIEW");
        assertThat(result.sections().get(1).questions().getFirst().correct()).isNull();

        var reviewed = PracticeGrader.grade(exam, attempt, Map.of(writingId,
                new WritingReview(attempt.id(), writingId, UUID.randomUUID(),
                        4, 3, 5, 4, "Clear idea; strengthen transitions.")));
        assertThat(reviewed.sections().get(1).questions().getFirst().status()).isEqualTo("REVIEWED");
        assertThat(reviewed.sections().get(1).questions().getFirst().writingFeedback().totalScore())
                .isEqualTo(16);
        assertThat(reviewed.total()).isEqualTo(1);
    }
}
