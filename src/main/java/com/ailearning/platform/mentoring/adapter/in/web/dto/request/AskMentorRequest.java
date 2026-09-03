package com.ailearning.platform.mentoring.adapter.in.web.dto.request;

import com.ailearning.platform.mentoring.domain.valueobject.MentorQuestion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskMentorRequest(
        @NotBlank(message = "Câu hỏi không được để trống.")
        @Size(max = MentorQuestion.MAX_LENGTH, message = "Câu hỏi không được vượt quá 4000 ký tự.")
        String question
) {
}
