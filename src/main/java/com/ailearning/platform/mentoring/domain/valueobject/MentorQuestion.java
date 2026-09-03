package com.ailearning.platform.mentoring.domain.valueobject;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

public record MentorQuestion(String value) {
    public static final int MAX_LENGTH = 4000;

    public MentorQuestion {
        value = value == null ? "" : value.strip();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new BusinessException(
                    "invalid_mentor_question",
                    ErrorType.BAD_REQUEST,
                    "Câu hỏi phải có từ 1 đến 4000 ký tự."
            );
        }
    }

    public static MentorQuestion from(String rawValue) {
        return new MentorQuestion(rawValue);
    }
}
