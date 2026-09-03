package com.ailearning.platform.mentoring.domain.valueobject;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MentorQuestionTest {
    @Test
    void trimsAValidQuestion() {
        assertThat(MentorQuestion.from("  Explain this pattern  ").value())
                .isEqualTo("Explain this pattern");
    }

    @Test
    void rejectsBlankAndOversizedQuestions() {
        assertThatThrownBy(() -> MentorQuestion.from("   "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> MentorQuestion.from("x".repeat(MentorQuestion.MAX_LENGTH + 1)))
                .isInstanceOf(BusinessException.class);
    }
}

