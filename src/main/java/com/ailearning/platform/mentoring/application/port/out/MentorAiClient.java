package com.ailearning.platform.mentoring.application.port.out;

import com.ailearning.platform.learning.api.usecase.mentoring.MentoringLessonContext;
import com.ailearning.platform.mentoring.domain.model.MentorMessage;

import java.util.List;
import java.util.function.Consumer;

public interface MentorAiClient {
    Result generate(
            MentoringLessonContext lesson,
            List<MentorMessage> messages,
            Consumer<String> deltaConsumer
    );

    record Result(String content, String model, int inputTokens, int outputTokens) {
    }
}

